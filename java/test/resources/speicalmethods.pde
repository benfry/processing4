ArrayList<Integer> positions = new ArrayList<>();

void setup() {
    size(100, 100);
    positions.add(25);
    positions.add(50);
    positions.add(75);
}

void draw() {
    for (int i = 0; i < positions.size(); i++) {
        ellipse(positions.get(i), positions.get(i), 10, 10);
    }
}

void mousePressed() {
    positions.add(mouseX);
}
