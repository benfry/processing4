import processing.pdf.*;

void setup() {
  int newWidth = 400*2;
  size(newWidth, 400/2);
}

void draw() {
  // Draw something good here
  line(0, 0, width/2, height);

  // Exit the program
  println("Finished.");
  exit();
}
