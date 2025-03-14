import processing.core.PApplet;

public class Basic extends PApplet {
    public void settings(){
        size(500, 500);
    }

    public void draw(){
        ellipse(width / 2f, height / 2f, 125f, 125f);
    }


    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[]{ Basic.class.getName()};
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }

    }
}
