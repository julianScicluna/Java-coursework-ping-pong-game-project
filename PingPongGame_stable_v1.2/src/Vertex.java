//A class to represent vertices with built in methods to manipulate them

import java.util.List;

public class Vertex {
    public double x, y;
    public Vertex(double x, double y) {
        this.x = x;
        this.y = y;
    }
    //Method to apply rotation transformation to vertex
    public Vertex rotateVertex(Vertex pivot, double angle) {
        //Angle accepted as degrees
        double cachedX = this.x;
        //Apply rotation matrix to vertex
        this.x = (cachedX - pivot.x) * Math.cos(angle * (Math.PI/180)) - (this.y - pivot.y) * Math.sin(angle * (Math.PI/180)) + pivot.x;
        this.y = (cachedX - pivot.x) * Math.sin(angle * (Math.PI/180)) + (this.y - pivot.y) * Math.cos(angle * (Math.PI/180)) + pivot.y;
        return this;
    }
    //Method to apply enlargement transformation to vertex
    public Vertex enlargeVertex(Vertex transformationCentre, double scaleFactor) {
        this.x = (this.x - transformationCentre.x) * scaleFactor + transformationCentre.x;
        this.y = (this.y - transformationCentre.y) * scaleFactor + transformationCentre.y;
        return this;
    }
    /*public Vertex shearVertex(Vertex middleVertex, double shearAmount) {

    }*/
    //Method to apply enlargement transformation to an abstract list of vertices
    public static List<Vertex> enlargeVertices(List<Vertex> list, Vertex transformationCentre, double scaleFactor) {
        for (Vertex v : list) {
            v.enlargeVertex(transformationCentre, scaleFactor);
        }
        return list;
    }
    //Method to apply rotation transformation to an abstract list of vertices
    public static List<Vertex> rotateVertices(List<Vertex> list, Vertex transformationCentre, double angle) {
        for (Vertex v : list) {
            v.rotateVertex(transformationCentre, angle);
        }
        return list;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ")";
    }
}