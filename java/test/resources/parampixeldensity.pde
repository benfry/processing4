void setup() {
  size(100, 100);
  // Pulling the display's density dynamically
  pixelDensity(displayDensity());
  noStroke();
}

void draw() {
  background(0);
  ellipse(30, 48, 36, 36);
  ellipse(70, 48, 36, 36);
}
