from fastapi import FastAPI

app = FastAPI(
    title="AccesibleBus API",
    description="API for AccessibleBus data management and analysis",
    version="1.0.0",
)

@app.get("/health")
def health():
    return {"status": "OK"}
