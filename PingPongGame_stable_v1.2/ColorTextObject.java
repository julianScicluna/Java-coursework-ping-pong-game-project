import java.awt.Color;

//An abstract class to represent colours and text for text animations
public class ColorTextObject {
    private String text = "";
    private Color color = Color.BLACK;

    public String getText() {
        return text;
    }
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        if (color == null) {
            //Code taken from https://www.geeksforgeeks.org/throw-throws-java/
            throw new NullPointerException("Cannot set color attribute of ColorTextObject to null");
        } else {
            this.color = color;
        }
    }
    public void setText(String text) {
        if (text == null) {
            throw new NullPointerException("Cannot set text attribute of ColorTextObject to null");
        } else {
            this.text = text;
        }
    }
}
