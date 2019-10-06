void setup() {
    size(100, 100);
}

@Override void draw() {
    ellipse(50, 50, 10, 10);
    Test t = new Test();
    t.draw();
}

class Test {
    private void draw() {}
}

