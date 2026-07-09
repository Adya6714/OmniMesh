from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="OmniMesh Local Inference (echo stub)", version="0.1.0")


class EchoRequest(BaseModel):
    message: str


class EchoResponse(BaseModel):
    echo: str
    backend: str = "local-inference-stub"


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "local-inference"}


@app.post("/echo", response_model=EchoResponse)
async def echo(request: EchoRequest) -> EchoResponse:
    return EchoResponse(echo=request.message)
