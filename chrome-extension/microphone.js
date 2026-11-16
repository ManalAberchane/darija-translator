const micStatus = document.getElementById("micStatus");

navigator.mediaDevices.getUserMedia({ audio: true })
  .then((stream) => {
    stream.getTracks().forEach((track) => track.stop());
    micStatus.textContent = "Microphone allowed. You can close this tab and use the 🎤 button in the side panel.";
    micStatus.className = "status ok";
  })
  .catch(() => {
    micStatus.textContent = "Microphone access was blocked. Allow it from the address bar icon, then reload this page.";
    micStatus.className = "status error";
  });
