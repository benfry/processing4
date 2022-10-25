int x = 0;

void setup() {
  background(0);
  noStroke();
  fill(102);
}

void draw() {
  rect(x, height*0.2, 1, height*0.6);
  x = x + 2;
}
