import processing.pdf.*;

void setup() {
  size(400, 400, PDF, "filename.pdf");
}

void draw() {
  // Draw something good here
  line(0, 0, width/2, height);

  // Exit the program
  println("Finished.");
  exit();
}
