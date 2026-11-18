import io
import os
import base64
from typing import Literal

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from openai import OpenAI
from PIL import Image, ImageOps

app = FastAPI(title="AI Photo Studio API", version="1.0.0")

MAX_UPLOAD_BYTES = 12 * 1024 * 1024
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}
MODEL = os.getenv("OPENAI_IMAGE_MODEL", "gpt-image-1.5")


@app.get("/health")
def health():
    return {"status": "ok", "service": "AI Photo Studio API"}


@app.post("/edit")
async def edit_photo(
    operation: Literal["remove_people", "slim_body"] = Form(...),
    strength: int = Form(25),
    image: UploadFile = File(...),
):
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="AI server is missing OPENAI_API_KEY.")

    if image.content_type not in ALLOWED_TYPES:
        raise HTTPException(status_code=415, detail="Upload a JPG, PNG, or WEBP image.")

    raw = await image.read()
    if not raw:
        raise HTTPException(status_code=400, detail="The uploaded image is empty.")
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Image is too large. Maximum upload is 12 MB.")

    try:
        with Image.open(io.BytesIO(raw)) as opened:
            oriented = ImageOps.exif_transpose(opened).convert("RGB")
            # Bound dimensions to keep cloud processing and upload costs manageable.
            oriented.thumbnail((1800, 1800))
            normalized = io.BytesIO()
            oriented.save(normalized, format="JPEG", quality=90, optimize=True)
            normalized.seek(0)
    except Exception:
        raise HTTPException(status_code=400, detail="Could not read the uploaded image.")

    if operation == "remove_people":
        prompt = (
            "Edit this exact photograph. Remove people who are clearly in the background "
            "and are not the main foreground subject. Reconstruct the areas behind them "
            "with a plausible continuation of the original scene, lighting, texture, and "
            "perspective. Preserve the main foreground subject, their face, body, clothing, "
            "pose, and all other scene details. Do not add new people or objects. "
            "If it is unclear who is the main subject, preserve the most prominent subject."
        )
    else:
        strength = max(0, min(60, strength))
        prompt = (
            "Make a subtle, natural body-proportion refinement to the person in this photo. "
            f"Requested strength: {strength} out of 60. Make only a modest and realistic "
            "adjustment to the body silhouette where appropriate. Preserve the person's "
            "identity, face, expression, pose, clothing design, hands, limbs, and the original "
            "background and camera perspective. Keep anatomy realistic. Do not exaggerate, "
            "do not change age, and do not alter other people in the photograph. "
            "If no clear person is present, return the image essentially unchanged."
        )

    try:
        client = OpenAI(api_key=api_key)
        response = client.images.edit(
            model=MODEL,
            image=("photo.jpg", normalized, "image/jpeg"),
            prompt=prompt,
            input_fidelity="high",
            output_format="png",
        )
        if not response.data or not response.data[0].b64_json:
            raise RuntimeError("The image service returned no image.")
        return {
            "image_base64": response.data[0].b64_json,
            "mime_type": "image/png",
        }
    except Exception as exc:
        # Do not return API credentials or internal exception traces to the phone.
        message = str(exc)
        if "billing" in message.lower() or "quota" in message.lower():
            raise HTTPException(status_code=402, detail="AI provider quota or billing limit reached.")
        raise HTTPException(status_code=502, detail="AI image editing failed. Check server logs and provider access.")
