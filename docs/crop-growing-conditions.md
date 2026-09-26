# Growing conditions for the project's 10 crops

Researched on 26 September 2026. The crop names match `CropType.java`.

These are **reference conditions for a student prototype**, not a validated crop-health prediction model. Variety, growth stage, soil, location and growing system affect the appropriate limits. A reading outside a reference range is a possible stress signal, not proof of disease or crop failure.

## Temperature, soil pH and precipitation

The numbers below are the **Optimal Min/Max** columns in the linked FAO ECOCROP data sheets, rather than their wider absolute limits. Temperature is a broad climatic suitability range, not a universal hourly alarm threshold. Precipitation is **annual rainfall in millimetres**, not daily rainfall and not an irrigation prescription.

| Project crop | Species / scope | Temperature (°C) | Soil pH | Annual rainfall (mm/year) | Direct source |
| --- | --- | --- | --- | --- | --- |
| Potato | *Solanum tuberosum* | 15–25 | 5.0–6.2 | 500–800 | [FAO potato data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1971) |
| Sugar cane | *Saccharum officinarum* | 24–37 | 5.0–8.0 | 1,500–2,000 | [FAO sugar cane data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1884) |
| Apple | *Malus domestica*; growing season | 14–27 | 6.0–7.0 | 700–2,500 | [FAO apple data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1407) |
| Rice | *Oryza sativa*; general wetland-rice reference | 20–30 | 5.5–7.0 | 1,500–2,000 | [FAO rice data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1574) |
| Wheat | *Triticum aestivum*; bread wheat | 15–23 | 6.0–7.0 | 750–900 | [FAO wheat data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=2114) |
| Maize | *Zea mays* | 18–33 | 5.0–7.0 | 600–1,200 | [FAO maize data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=2175) |
| Tomato | *Solanum lycopersicum*, listed as *Lycopersicon esculentum* | 20–27 | 5.5–6.8 | 600–1,300 | [FAO tomato data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1379) |
| Carrot | *Daucus carota* | 15–24 | 5.8–6.8 | 600–1,200 | [FAO carrot data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=871) |
| Lettuce | *Lactuca sativa* var. *capitata*; head lettuce | 12–21 | 6.0–7.0 | 1,100–1,400 | [FAO lettuce data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1313) |
| Soybean | *Glycine max* | 20–33 | 5.5–6.5 | 600–1,500 | [FAO soybean data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1150) |

**How to cross-reference:** open a data sheet and look under **Ecology → Optimal**, in the rows **Temperat. requir.**, **Soil PH**, and **Rainfall (annual)**. The adjacent **Absolute** columns mean something different.

ECOCROP is a legacy database, discontinued around 2015 and subsequently made available through GAEZ. Its broad species ranges are useful for background research, but are not current local cultivation advice. See [FAO's explanation of ECOCROP](https://www.fao.org/geospatial/data-and-tools/data-portals/ecocrop/).

## Weather and air humidity

Humidity here means **relative humidity of the air (RH)**, not water in soil or harvested grain. Qualitative descriptions stay qualitative: “moderate” does not automatically mean an invented percentage band. “Not established” means the sources reviewed did not establish a defensible general numerical optimum.

| Crop | Favourable weather / important limitations | Humidity guidance | Source |
| --- | --- | --- | --- |
| Potato | Cool growing conditions with strong light; cool, persistently wet conditions also favour late blight. | General optimum RH not established. Prolonged dampness is a disease-risk consideration, not a diagnosis. | [FAO potato](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1971); [University of Minnesota late blight guide](https://extension.umn.edu/agriculture/specialty-crops/vegetable-farming/disease-management/late-blight) |
| Sugar cane | Warm, sunny growing season; cooler, drier weather helps ripening. | **80–85% RH during rapid stem growth**; **45–65% RH during ripening**, with limited water supply. These are stage-specific. | [Tamil Nadu Agricultural University, “Relative humidity”](https://agritech.tnau.ac.in/expert_system/sugar/botany&climate.html); [FAO crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=1884) |
| Apple | Bright growing conditions, warm days and cool nights; winter chilling is also needed and varies by cultivar. | Low to medium humidity preferred; numerical optimum not established. | [FAO apple crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=1407) |
| Rice | Warm, bright, frost-free conditions; water management depends on whether rice is irrigated lowland, rainfed or upland. | Medium to high humidity preferred; can also grow in dry air with adequate irrigation. No universal percentage established. | [FAO rice data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1574); [FAO rice crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=1574) |
| Wheat | Bright conditions, suitable cool-season temperatures; hot, humid conditions are unfavourable. Winter and spring wheat differ. | Relatively dry air preferred. No numerical RH range recommended here. | [FAO wheat crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=2114) |
| Maize | Warm, bright conditions; hot, dry winds can interfere with pollination, and hail can damage plants. | Avoid very humid conditions; numerical optimum not established. | [FAO maize data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=2175); [FAO grain maize crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=238663) |
| Tomato | Warm, bright conditions; wet, dull weather hinders ripening and hot, dry winds can cause flower loss. | High RH can be harmful. No general outdoor RH percentage adopted here. | [FAO tomato crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=1379) |
| Carrot | Cool-season conditions; prolonged heat reduces root quality. | Moderate humidity; numerical optimum not established. | [FAO carrot crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=871) |
| Lettuce | Cool, adequately lit conditions; high temperatures can cause poor growth and bitter leaves. | Medium to high humidity; numerical optimum not established. | [FAO lettuce data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1313); [FAO lettuce crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=1313) |
| Soybean | Warm, bright conditions; choose a variety adapted to local day length. | Moderate humidity preferred, with a fairly broad tolerance; numerical optimum not established. | [FAO soybean data sheet](https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=1150); [FAO soybean crop notes](https://ecocrop.apps.fao.org/ecocrop/srv/en/cropView?id=1150) |

Source caution: the legacy wheat notes include an unusually low RH figure of about 10% without enough context for a general rule. This report does **not** adopt it as an optimal threshold. Likewise, humidity values for storing potatoes or grain must not be used as growing conditions.

## Soil moisture

There is no single transferable sensor percentage for every soil. For example, 25% volumetric water content can mean different things in sand and clay. First establish the soil's **field capacity** (water remaining after drainage) and **permanent wilting point** (the lower limit of plant-available water). See [University of Minnesota's soil-moisture sensor guidance](https://extension.umn.edu/natural-resources/conservation/agricultural-soil-and-water/irrigation/soil-moisture-sensors-for-irrigation-scheduling).

For nine crops, FAO provides a **no-stress depletion fraction**: the share of plant-available root-zone water that can be used before stress begins, under the stated assumptions. It is **not a target volumetric water percentage**. These reference values assume crop evapotranspiration near **5 mm/day** and need adjustment for atmospheric demand, rooting depth and soil. Source for all nine values: [FAO Irrigation and Drainage Paper 56, Chapter 8, Table 22](https://www.fao.org/4/x0490e/x0490e0e.htm).

| Crop | Reference depletion of available root-zone water |
| --- | --- |
| Potato | 35% |
| Sugar cane | 65% |
| Apple | 50% |
| Wheat | 55% |
| Maize | 55% for grain maize; 50% for sweet maize |
| Tomato | 40% |
| Carrot | 35% |
| Lettuce | 30% |
| Soybean | 50% |

For **irrigated lowland rice**, a more practical reference is IRRI's safe Alternate Wetting and Drying method: re-irrigate when the water level in a field tube reaches about **15 cm below the soil surface**, then flood to about **5 cm above it**. From one week before flowering until one week after flowering, keep the field flooded, topping up to 5 cm. This is a water-level rule for that production system, not a percentage or a rule for all rice. Source: [IRRI Rice Knowledge Bank — Saving Water with AWD](https://www.knowledgebank.irri.org/training/fact-sheets/water-management/saving-water-alternate-wetting-drying-awd).

## How your team can use this research

These are implementation recommendations, not additional findings from the sources:

1. Keep the source URL, units, crop variety/system and growth stage alongside every reference value. Keep unknown numeric limits empty rather than guessing them.
2. Use the temperature and pH ranges as clearly labelled preliminary comparisons. Add stage-specific rules before claiming to predict whether the crop will thrive.
3. Use annual rainfall for climate suitability. **Do not compare today's rainfall to an annual range or divide that range by 365 to create a daily target.** For watering advice, account for recent effective rainfall, irrigation, root-zone moisture and forecast demand. FAO explains why water need varies with weather, crop and stage in [Crop Water Needs](https://www.fao.org/4/s2022e/s2022e02.htm).
4. Agree with the API teammate what “soil moisture” means: volumetric water content, water saturation, available-water fraction, or something else. Record the soil depth, measurement time and whether the value is measured or modelled.
5. Do not turn a forecast into a claim about a current measurement. Missing soil pH or soil-moisture data should produce “not assessed,” not a healthy score.
6. Describe weather-related disease conditions as **risk**, not confirmed disease. For example, damp potato weather can justify “inspect leaves and improve airflow”; it does not prove blight.

No values in this document have been wired into the live health overview. The current application still needs your team's measurement integration and agreed assessment rules.
