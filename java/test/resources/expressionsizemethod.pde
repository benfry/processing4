import processing.pdf.*;

int getWidth() {
  return 400*2;
}

void setup() {
  size(getWidth(), 400/2);
}

void draw() {
  // Draw something good here
  line(0, 0, width/2, height);

  // Exit the program
  println("Finished.");
  exit();
}
