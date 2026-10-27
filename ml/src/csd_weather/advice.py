"""Conservative inspection advice, kept separate from the biomass estimate."""
from __future__ import annotations


def weather_advice(features: dict[str, float] | None) -> list[dict]:
    if features is None:
        return [{"code": "CHECK_INPUTS", "message": "Weather evidence is incomplete or unsupported. Check the location, sowing date and weather record before interpreting growth."}]
    advice = [{"code": "FIELD_CHECK", "message": "Compare this estimate with dated field observations. Weather alone cannot establish crop health or identify the cause of poor growth."}]
    temperature = features["temperature_mean_7d"]
    if temperature < 15 or temperature > 23:
        advice.append({
            "code": "TEMPERATURE_REFERENCE", "evidence": "BROAD_ECOLOGICAL_REFERENCE",
            "message": "The recent average temperature is outside the broad 15–23°C wheat reference. Check crop stage and local guidance before taking action; this is not a damage threshold.",
            "source": "https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=2114",
        })
    advice.append({"code": "CHECK_ROOT_ZONE", "message": "Confirm moisture in the crop's root zone and consider recent rain and irrigation before watering. The model's shallow soil estimate is not an irrigation prescription."})
    return advice
