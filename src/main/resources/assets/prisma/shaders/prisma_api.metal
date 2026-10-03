#include <metal_stdlib>
using namespace metal;

struct CameraData {
    float aspect;
    float fovScale;
    float cameraPitch;
    float cameraYaw;
    float gameTime;
};

struct EnvironmentData {
    float sunAngle;
    float skyR;
    float skyG;
    float skyB;
    float sunriseAlpha;
    float sunriseR;
    float sunriseG;
    float sunriseB;
    float starBrightness;
    float rainStrength;
};

struct RenderSettings {
    float sunShadowsEnabled;
    float waterWaveStrength;
    float waterWaveSpeed;
    float waterAbsorption;
    float maxPointLights;
    float reflectionPtShadows;
    float reflectionDirShadows;
    float doubleAoInReflections;
    float cloudsEnabled;
    float cloudSteps;
    float reflectionsEnabled;
    float cloudsInReflections;
    float pointLightSoftShadows;
    float shadowRayCount;
    float volFogEnabled;
    float volFogSamples;
    float volFogIntensity;
    float waterOnlyPass;
    float hdrEnabled;
};
