import * as faceapi from '@vladmandic/face-api';

const MODEL_URL = 'https://cdn.jsdelivr.net/npm/@vladmandic/face-api/model';
let modelsPromise;

export const FACE_MATCH_THRESHOLD = Number(import.meta.env.VITE_FACE_MATCH_THRESHOLD || 0.6);

export const loadFaceModels = () => {
  if (!modelsPromise) {
    modelsPromise = Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
      faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
      faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL)
    ]);
  }
  return modelsPromise;
};

export const getFaceDescriptors = async (source, maxResults = 3) => {
  await loadFaceModels();
  return faceapi.detectAllFaces(source, new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.5 }))
    .withFaceLandmarks()
    .withFaceDescriptors()
    .then(results => results.slice(0, maxResults));
};

export const descriptorDistance = (left, right) => faceapi.euclideanDistance(left, right);
