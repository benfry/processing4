class Button {
  
  int x, y, radius;
  
  public Button (int x, int y, int radius) {
    this.x = x;
    this.y = y;
    this.radius = radius;
  }
  
  boolean over() {
    return dist(mouseX, mouseY, this.x, this.y) < this.radius;
  }
  
  void draw() {
    ellipse(this.x, this.y, this.radius * 2, this.radius * 2);
  }
  
}
