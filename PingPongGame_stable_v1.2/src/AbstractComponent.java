import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.ImageObserver;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JComponent;
import javax.swing.border.Border;

import Helpers.ArrayHelpers;

public abstract class AbstractComponent {
	protected static EventDispatchThread componentEventDispatchThread = new EventDispatchThread();
	protected JComponent drawComponent;
	private static LinkedList<AbstractComponent> registeredComponents = new LinkedList<AbstractComponent>();
	public static LinkedList<AbstractComponent> getRegisteredComponents() {
		return registeredComponents;
	}

	public static EventDispatchThread getEventDispatchThread() {
		return componentEventDispatchThread;
	}

	public abstract void paint(Graphics2D g2d);

    protected LinkedList<ActionListener> listeners = new LinkedList<ActionListener>();
    protected LinkedList<ComponentMouseListener> mouseListeners = new LinkedList<ComponentMouseListener>();
    public void addActionListener(ActionListener listener) {
        listeners.add(listener);
    }
    public void addMouseListener(ComponentMouseListener listener) {
        mouseListeners.add(listener);
	}

	public JComponent getDrawComponent() {
		return drawComponent;
	}

    public void removeActionListener(ActionListener listener) {
        listeners.remove(listener);
    }
    public void removeMouseListener(ComponentMouseListener listener) {
        mouseListeners.remove(listener);
    }

    public void remove() {
		registeredComponents.remove(this);
		while (this.listeners.size() != 0) {
			this.listeners.remove(0);
		}
		while (this.mouseListeners.size() != 0) {
			this.mouseListeners.remove(0);
		}
	}

	
	protected double x, y, width, height;
	protected float rotation;
	protected boolean isVisible;

	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
	}

	public double getWidth() {
		return width;
	}
	public void setWidth(double width) {
		this.width = width;
	}

	public double getHeight() {
		return height;
	}
	public void setHeight(double height) {
		this.height = height;
	}

	public float getRotation() {
		return rotation;
	}
	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public boolean isVisible() {
		return isVisible;
	}
	public void setVisible(boolean isVisible) {
		this.isVisible = isVisible;
	}

	public AbstractComponent(double x, double y, double width, double height, float rotation, JComponent drawComponent) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.rotation = rotation;
		this.drawComponent = drawComponent;
		this.isVisible = true;
		registeredComponents.add(this);
	}
}

class Button extends AbstractComponent implements CoordinateRatiosable {
    private static LinkedList<Button> registeredButtons = new LinkedList<Button>();
	public static LinkedList<Button> getRegisteredButtons() {
		return registeredButtons;
	}
	private static final MouseListener drawPanelListener = new MouseListener() {
        @Override
		public void mousePressed(MouseEvent e) {
			Vertex clickVertex;
			for (Button button : registeredButtons) {
				clickVertex = new Vertex(e.getX(), e.getY()).rotateVertex(new Vertex(button.x + button.width/2, button.y + button.height/2), -button.rotation);
				if (clickVertex.x >= button.getAbsoluteScreenX() && clickVertex.y >= button.getAbsoluteScreenY() &&
					clickVertex.x < button.getAbsoluteScreenX() + button.getWidth() && clickVertex.y < button.getAbsoluteScreenY() + button.getHeight()
 				) {
					button.mouseDown = true;
					for (ComponentMouseListener listener : button.mouseListeners) {
						componentEventDispatchThread.submit(new Runnable() {
							@Override
							public void run() {
								listener.mousePressed(new CustomMouseEvent(button, CustomMouseEvent.MOUSE_MOVE, System.currentTimeMillis(), (int) (e.getX() - button.x), (int) (e.getY() - button.y)));
							}
						});
					}
					//Put break statement here - only one button can be triggered per click (in the possible case of overlapping buttons)
					break;
				}
			}
		}
        @Override
		public void mouseReleased(MouseEvent e) {
			Vertex clickVertex;
			for (Button button : registeredButtons) {
				button.mouseDown = false;
				clickVertex = new Vertex(e.getX(), e.getY()).rotateVertex(new Vertex(button.x + button.width/2, button.y + button.height/2), -button.rotation);
				if (clickVertex.x >= button.getAbsoluteScreenX() && clickVertex.y >= button.getAbsoluteScreenY() &&
					clickVertex.x < button.getAbsoluteScreenX() + button.getWidth() && clickVertex.y < button.getAbsoluteScreenY() + button.getHeight()
 				) {
					for (ComponentMouseListener listener : button.mouseListeners) {
						componentEventDispatchThread.submit(new Runnable() {
							@Override
							public void run() {
								listener.mouseReleased(new CustomMouseEvent(button, CustomMouseEvent.MOUSE_UP, System.currentTimeMillis(), (int) (e.getX() - button.x), (int) (e.getY() - button.y)));
							}
						});
					}
				}
			}
		}
        @Override
		public void mouseClicked(MouseEvent e) {
			Vertex clickVertex;
			for (Button button : registeredButtons) {
				clickVertex = new Vertex(e.getX(), e.getY()).rotateVertex(new Vertex(button.getX() + button.getWidth()/2, button.getY() + button.getHeight()/2), -button.rotation);
				if (clickVertex.x >= button.getAbsoluteScreenX() && clickVertex.y >= button.getAbsoluteScreenY() &&
					clickVertex.x < button.getAbsoluteScreenX() + button.getWidth() && clickVertex.y < button.getAbsoluteScreenY() + button.getHeight()
 				) {
					for (ActionListener listener : button.listeners) {
						componentEventDispatchThread.submit(new Runnable() {
							@Override
							public void run() {
								listener.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, ""));
							}
						});
					}
					//Put break statement here - only one button can be triggered per click (in the possible case of overlapping buttons)
					break;
				}
			}
		}
        @Override
		public void mouseEntered(MouseEvent e) {
			//No need to enter anything here
		}
        @Override
		public void mouseExited(MouseEvent e) {
			//No need to enter anything here
		}
	};
    private static final MouseMotionListener drawPanelMotionListener = new MouseMotionListener() {
        @Override
        public void mouseMoved(MouseEvent e) {
			Vertex clickVertex;
			for (Button button : registeredButtons) {
				clickVertex = new Vertex(e.getX(), e.getY()).rotateVertex(new Vertex(button.getAbsoluteScreenX() + button.getWidth()/2, button.getAbsoluteScreenY() + button.getHeight()/2), -button.getRotation());
				if (clickVertex.x >= button.getAbsoluteScreenX() && clickVertex.y >= button.getAbsoluteScreenY() &&
					clickVertex.x < button.getAbsoluteScreenX() + button.getWidth() && clickVertex.y < button.getAbsoluteScreenY() + button.getHeight()
 				) {
					//Can be simplified to button.mouseOver = <condition>, however it is more readable this way
					for (ComponentMouseListener mouseListener : button.mouseListeners) {
						componentEventDispatchThread.submit(new Runnable() {
							@Override
							public void run() {
								mouseListener.mouseMoved(new CustomMouseEvent(button, CustomMouseEvent.MOUSE_MOVE, System.currentTimeMillis(), (int) (e.getX() - button.x), (int) (e.getY() - button.y)));
							}
						});
						if (!button.mouseOver) {
							componentEventDispatchThread.submit(new Runnable() {
								@Override
								public void run() {
									mouseListener.mouseEntered(new CustomMouseEvent(button, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), e.getX(), e.getY()));
								}
							});
						}
					}
					button.mouseOver = true;
				} else {
					for (ComponentMouseListener mouseListener : button.mouseListeners) {
						if (button.mouseOver) {
							componentEventDispatchThread.submit(new Runnable() {
								@Override
								public void run() {
									mouseListener.mouseExited(new CustomMouseEvent(button, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), e.getX(), e.getY()));
								}
							});
						}
					}
					button.mouseOver = false;
				}
				//DO NOT put break statement - must change the state of ALL the buttons
			}
        }
        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    private boolean mouseOver = false, mouseDown = false;
	private float xScreenRatio, yScreenRatio;
	protected Color textColor, bgColor;
	protected Font textFont;
	protected String text;
	protected int curvedEdgesRadius = 0;

	@Override
	public double getAbsoluteScreenX() {
		return this.x + this.xScreenRatio * this.drawComponent.getWidth();
	}

	@Override
	public double getAbsoluteScreenY() {
		return this.y + this.yScreenRatio * this.drawComponent.getHeight();
	}

	@Override
	public double getWidth() {
		if (this.width == CoordinateRatiosable.DYNAMIC_COMPUTE) {
			return this.drawComponent.getFontMetrics(this.textFont).stringWidth(this.text) + 10;
		} else {
			return this.width;
		}
	}

	@Override
	public double getHeight() {
		if (this.height == CoordinateRatiosable.DYNAMIC_COMPUTE) {
			return this.drawComponent.getFontMetrics(this.textFont).getAscent() + 10;
		} else {
			return this.height;
		}
	}

	public Color getTextColor() {
		return textColor;
	}
	public void setTextColor(Color textColor) {
		this.textColor = textColor;
	}

	public Color getBgColor() {
		return bgColor;
	}
	public void setBgColor(Color bgColor) {
		this.bgColor = bgColor;
	}

	public Font getTextFont() {
		return textFont;
	}
	public void setTextFont(Font textFont) {
		this.textFont = textFont;
	}

	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}

	public int getCurvedEdgesRadius() {
		return curvedEdgesRadius;
	}
	public void setCurvedEdgesRadius(int curvedEdgesRadius) {
		this.curvedEdgesRadius = curvedEdgesRadius;
	}

	public float getXScreenRatio() {
		return this.xScreenRatio;
	}
	public float getYScreenRatio() {
		return this.yScreenRatio;
	}

	public void setXScreenRatio(float newValue) {
		this.xScreenRatio = newValue;
	}
	public void setYScreenRatio(float newValue) {
		this.yScreenRatio = newValue;
	}

	protected boolean getMouseOver() {
		return mouseOver;
	}
	protected boolean getMouseDown() {
		return mouseDown;
	}
    public Button(double x, double y, double width, double height, float rotation, JComponent component, String text, Color textColor, Color bgColor, Font textFont) {
		super(x, y, width, height, rotation, component);
		this.xScreenRatio = 0;
		this.yScreenRatio = 0;
		this.textColor = textColor;
		this.bgColor = bgColor;
		this.textFont = textFont;
		if (text == null) {
			throw new NullPointerException("String object for Button's text in Button constructor cannot be null!");
		} else {
			this.text = text;
		}
        registeredButtons.add(this);
		if (ArrayHelpers.indexOf(this.drawComponent.getMouseListeners(), drawPanelListener) == -1) {
			this.drawComponent.addMouseListener(drawPanelListener);
		}
		if (ArrayHelpers.indexOf(this.drawComponent.getMouseMotionListeners(), drawPanelMotionListener) == -1) {
			this.drawComponent.addMouseMotionListener(drawPanelMotionListener);
		}
	}
    public Button(double x, double y, float xScreenRatio, float yScreenRatio, double width, double height, float rotation, JComponent component, String text, Color textColor, Color bgColor, Font textFont) {
		super(x, y, width, height, rotation, component);
		this.xScreenRatio = xScreenRatio;
		this.yScreenRatio = yScreenRatio;
		this.textColor = textColor;
		this.bgColor = bgColor;
		this.textFont = textFont;
		if (text == null) {
			throw new NullPointerException("String object for Button's text in Button constructor cannot be null!");
		} else {
			this.text = text;
		}
        registeredButtons.add(this);
		if (ArrayHelpers.indexOf(this.drawComponent.getMouseListeners(), drawPanelListener) == -1) {
			this.drawComponent.addMouseListener(drawPanelListener);
		}
		if (ArrayHelpers.indexOf(this.drawComponent.getMouseMotionListeners(), drawPanelMotionListener) == -1) {
			this.drawComponent.addMouseMotionListener(drawPanelMotionListener);
		}
	}
    public void remove() {
        registeredButtons.remove(this);
        super.remove();
    }
	private FontMetrics fm;
    public void paint(Graphics2D g2d) {
		synchronized(this) {
			if (this.isVisible) {
				Color computedBgColor;
				fm = g2d.getFontMetrics(this.textFont);
				double computedWidth, computedHeight;
				if (this.mouseDown) {
					computedBgColor = new Color(3 * this.bgColor.getRed() / 4, 3 * this.bgColor.getGreen() / 4, 3 * this.bgColor.getBlue() / 4, this.bgColor.getAlpha() /*Alpha must remain the same*/);
				} else if (this.mouseOver) {
					computedBgColor = new Color(5 * this.bgColor.getRed() / 6, 5 * this.bgColor.getGreen() / 6, 5 * this.bgColor.getBlue() / 6, this.bgColor.getAlpha() /*Alpha must remain the same*/);
				} else {
					computedBgColor = new Color(this.bgColor.getRed(), this.bgColor.getGreen(), this.bgColor.getBlue(), this.bgColor.getAlpha());
				}
				if (this.width == CoordinateRatiosable.DYNAMIC_COMPUTE) {
					if (this.text == null) {
						computedWidth = 10;
					} else {
						computedWidth = fm.stringWidth(this.text) + 10;
					}
				} else {
					computedWidth = this.width;
				}
				if (this.height == CoordinateRatiosable.DYNAMIC_COMPUTE) {
					if (this.text == null) {
						computedHeight = 10;
					} else {
						computedHeight = fm.getAscent() + 10;
					}
				} else {
					computedHeight = this.height;
				}
				g2d.translate(this.xScreenRatio * this.drawComponent.getWidth() + this.x + computedWidth/2, this.yScreenRatio * this.drawComponent.getHeight() + this.y + computedHeight/2);
				g2d.rotate(this.rotation * (Math.PI/180));
				if (this.curvedEdgesRadius == 0) {
					g2d.setColor(computedBgColor);
					g2d.fillRect((int) (-computedWidth/2), (int) (-computedHeight/2), (int) computedWidth, (int) computedHeight);
					g2d.setColor(Color.BLACK);
					g2d.drawRect((int) (-computedWidth/2), (int) (-computedHeight/2), (int) computedWidth, (int) computedHeight);
					g2d.setColor(this.textColor);
					g2d.setFont(this.textFont);
					g2d.drawString(this.text, (int) (-fm.stringWidth(this.text))/2, (int) (fm.getAscent())/2);
				} else {
					//Can cause MAJOR performance problems - preferably the paint method is overriden which draws a BufferedImage instead
					g2d.setColor(computedBgColor);

					//Middle rectangle
					g2d.fillRect((int) (this.curvedEdgesRadius - computedWidth/2), (int) (this.curvedEdgesRadius - computedHeight/2), (int) (computedWidth - this.curvedEdgesRadius), (int) (computedHeight - 2 * this.curvedEdgesRadius));

					//Top rectangle and top-left arc
					g2d.fillRect((int) (this.curvedEdgesRadius - computedWidth/2), (int) (-computedHeight/2), (int) (computedWidth - 2 * this.curvedEdgesRadius), (int) this.curvedEdgesRadius);
					g2d.fillArc((int) (-computedWidth/2), (int) (-computedHeight/2), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 90, 90);
					//Right rectangle and top-right arc
					g2d.fillRect((int) (computedWidth/2 - this.curvedEdgesRadius), (int) (this.curvedEdgesRadius - computedHeight/2), (int) (this.curvedEdgesRadius), (int) (computedHeight - 2 * this.curvedEdgesRadius));
					g2d.fillArc((int) (computedWidth/2 - 2 * this.curvedEdgesRadius), (int) (-computedHeight/2), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 0, 90);
					//Bottom rectangle and bottom-right arc
					g2d.fillRect((int) (this.curvedEdgesRadius - computedWidth/2), (int) (computedHeight/2 - this.curvedEdgesRadius), (int) (computedWidth - 2 * this.curvedEdgesRadius), (int) this.curvedEdgesRadius);
					g2d.fillArc((int) (computedWidth/2 - 2 * this.curvedEdgesRadius), (int) (computedHeight/2 - 2 * this.curvedEdgesRadius), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 270, 90);
					//Left rectangle and bottom-left arc
					g2d.fillRect((int) (-computedWidth/2), (int) (this.curvedEdgesRadius - computedHeight/2), (int) this.curvedEdgesRadius, (int) computedHeight - 2 * this.curvedEdgesRadius);
					g2d.fillArc((int) (-computedWidth/2), (int) (computedHeight/2 - 2 * this.curvedEdgesRadius), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 180, 90);


					//Setting button outline's colour
					g2d.setColor(Color.BLACK);

					//Draw top line and top-left arc
					g2d.drawLine((int) (this.curvedEdgesRadius - computedWidth/2), (int) (-computedHeight/2), (int) (computedWidth/2 - this.curvedEdgesRadius), (int) (-computedHeight/2));
					g2d.drawArc((int) (-computedWidth/2), (int) (-computedHeight/2), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 90, 90);
					//Draw right line and top-right arc
					g2d.drawLine((int) (computedWidth/2), (int) (this.curvedEdgesRadius - computedHeight/2), (int) (computedWidth/2), (int) (computedHeight/2 - this.curvedEdgesRadius));
					g2d.drawArc((int) (computedWidth/2 - 2 * this.curvedEdgesRadius), (int) (-computedHeight/2), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 0, 90);
					//Draw bottom line and bottom-right arc
					g2d.drawLine((int) (this.curvedEdgesRadius - computedWidth/2), (int) (computedHeight/2), (int) (computedWidth/2 - this.curvedEdgesRadius), (int) (computedHeight/2));
					g2d.drawArc((int) (computedWidth/2 - 2 * this.curvedEdgesRadius), (int) (computedHeight/2 - 2 * this.curvedEdgesRadius), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 270, 90);
					//Draw left line and bottom-left arc
					g2d.drawLine((int) (-computedWidth/2), (int) (this.curvedEdgesRadius - computedHeight/2), (int) (-computedWidth/2), (int) (computedHeight/2 - this.curvedEdgesRadius));
					g2d.drawArc((int) (-computedWidth/2), (int) (computedHeight/2 - 2 * this.curvedEdgesRadius), 2 * this.curvedEdgesRadius, 2 * this.curvedEdgesRadius, 180, 90);


					g2d.setColor(this.textColor);
					g2d.setFont(this.textFont);
					g2d.drawString(this.text, (int) (-fm.stringWidth(this.text))/2, (int) (fm.getAscent())/2);
				}
				g2d.rotate(-this.rotation * (Math.PI/180));
				g2d.translate(-(this.xScreenRatio * this.drawComponent.getWidth() + this.x + computedWidth/2), -(this.yScreenRatio * this.drawComponent.getHeight() + this.y + computedHeight/2));
			}
		}
    }
}

class Image extends AbstractComponent {
	private java.awt.Image image;
	private String caption;
	private boolean doBorder, doCaption;
	private Color borderColor;
	private BasicStroke borderStroke;
	@Override
	public void paint(Graphics2D g2d) {
		if (this.isVisible) {
			FontMetrics fm = g2d.getFontMetrics();
			if (this.doCaption) {
				g2d.drawImage(this.image, (int) this.x, (int) this.y, (int) this.width, (int) (this.height - fm.getAscent() - 5), this.drawComponent);
				g2d.drawString(this.caption, 5, (int) (this.height - fm.getAscent()));
			} else {
				g2d.drawImage(this.image, (int) this.x, (int) this.y, (int) this.width, (int) this.height, this.drawComponent);
			}
			if (this.doBorder) {
				g2d.setColor(this.borderColor);
				g2d.setStroke(this.borderStroke);
				g2d.drawRect((int) this.x, (int) this.y, (int) this.width, (int) this.height);
			}
		}
	}
	public Image(double x, double y, double width, double height, float rotation, JComponent component, java.awt.Image image) {
		super(x, y, width, height, rotation, component);
		this.doBorder = false;
		this.doCaption = false;
		this.image = image;
	}
	
	public java.awt.Image getImage() {
		return image;
	}
	public String getCaption() {
		return caption;
	}
	public Color getBorderColor() {
		return borderColor;
	}
	public BasicStroke getBorderStroke() {
		return borderStroke;
	}
	public boolean doBorder() {
		return doBorder;
	}
	public boolean doCaption() {
		return doCaption;
	}

	public void setImage(java.awt.Image image) {
		this.image = image;
	}
	public void setCaption(String caption) {
		this.caption = caption;
	}
	public void setBorderColor(Color borderColor) {
		this.borderColor = borderColor;
	}
	public void setBorderStroke(BasicStroke borderStroke) {
		this.borderStroke = borderStroke;
	}
	public void setDoBorder(boolean doBorder) {
		this.doBorder = doBorder;
	}
	public void setDoCaption(boolean doCaption) {
		this.doCaption = doCaption;
	}

}

class TextComponent extends AbstractComponent {
	private static LinkedList<TextComponent> registeredTextComponents = new LinkedList<TextComponent>();
	public static LinkedList<TextComponent> getRegisteredTextComponents() {
		return registeredTextComponents;
	}

	protected Color color;
	protected Font font;
	protected String text;
	protected float xScreenRatio, yScreenRatio;

	public Color getColor() {
		return color;
	}
	public void setColor(Color color) {
		this.color = color;
	}

	public Font getFont() {
		return font;
	}
	public void setFont(Font font) {
		this.font = font;
	}

	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}

	public float getxScreenRatio() {
		return xScreenRatio;
	}
	public void setxScreenRatio(float xScreenRatio) {
		this.xScreenRatio = xScreenRatio;
	}

	public float getyScreenRatio() {
		return yScreenRatio;
	}
	public void setyScreenRatio(float yScreenRatio) {
		this.yScreenRatio = yScreenRatio;
	}

	@Override
	public void paint(Graphics2D g2d) {
		synchronized (this) {
			if (this.isVisible) {
				FontMetrics fm = g2d.getFontMetrics(this.font);
				g2d.setFont(this.font);
				g2d.setColor(this.color);
				g2d.translate(this.xScreenRatio * drawComponent.getWidth() + this.x, this.yScreenRatio * drawComponent.getHeight() + this.y);
				g2d.rotate(this.rotation * (Math.PI/180));
				g2d.drawString(this.text, -fm.stringWidth(this.text)/2, -fm.getAscent()/2);
				g2d.rotate(-this.rotation * (Math.PI/180));
				g2d.translate(-(this.xScreenRatio * drawComponent.getWidth() + this.x), -(this.yScreenRatio * drawComponent.getHeight() + this.y));
			}
		}
	}

	public void remove() {
		registeredTextComponents.remove(this);
		super.remove();
	}

	public TextComponent(double x, double y, float rotation, JComponent drawComponent, Color color, Font font, String text) {
		super(x, y, 0, 0, rotation, drawComponent);
		this.xScreenRatio = 0;
		this.yScreenRatio = 0;
		this.color = color;
		this.font = font;
		this.text = text;
		registeredTextComponents.add(this);
	}
	public TextComponent(double x, double y, float rotation, JComponent drawComponent, float xRat, float yRat, Color color, Font font, String text) {
		super(x, y, 0, 0, rotation, drawComponent);
		this.xScreenRatio = xRat;
		this.yScreenRatio = yRat;
		this.color = color;
		this.font = font;
		this.text = text;
		registeredTextComponents.add(this);
	}
}

class Counter {
	private static LinkedList<Counter> registeredCounters = new LinkedList<Counter>();
	public static LinkedList<Counter> getRegisteredCounters() {
		return registeredCounters;
	}
	private FontMetrics fm;
	String key;
	String value;
	Color color;
	Font font;
	double x, y;
	float rotation, xScreenRatio, yScreenRatio;
	boolean isVisible = true;
	public Counter(double x, double y, float rotation, float xRat, float yRat, String key, String value, Color color, Font font) {
		this.x = x;
		this.y = y;
		this.rotation = rotation;
		this.key = key;
		this.value = value;
		this.color = color;
		this.font = font;
		this.xScreenRatio = xRat;
		this.yScreenRatio = yRat;
		registeredCounters.add(this);
	}
	public Counter(double x, double y, float rotation, String key, String value, Color color, Font font) {
		this.x = x;
		this.y = y;
		this.rotation = rotation;
		this.key = key;
		this.value = value;
		this.color = color;
		this.font = font;
		this.xScreenRatio = 0;
		this.yScreenRatio = 0;
		registeredCounters.add(this);
	}
	public void paint(Graphics2D g2d, JComponent component) {
		if (this.isVisible) {
			g2d.setFont(this.font);
			g2d.setColor(this.color);
			fm = g2d.getFontMetrics(this.font);
			g2d.translate((int) (this.xScreenRatio * component.getWidth() + this.x), (int) (this.yScreenRatio * component.getHeight() + this.y + fm.getAscent()/2));
			g2d.rotate(this.rotation * (Math.PI/180));
			g2d.drawString(this.key + ": " + this.value, -fm.stringWidth(this.key + ": " + this.value)/2, -fm.getAscent()/2);
			g2d.rotate(-this.rotation * (Math.PI/180));
			g2d.translate((int) -(this.xScreenRatio * component.getWidth() + this.x), (int) -(this.yScreenRatio * component.getHeight() + this.y + fm.getAscent()/2));
		}
	}
	public void remove() {
		registeredCounters.remove(this);
	}
}

class CustomMouseEvent {
	public static final int MOUSE_ENTER = 1, MOUSE_EXIT = 2, MOUSE_DOWN = 3, MOUSE_MOVE = 4, MOUSE_UP = 5, MOUSE_CLICK = 6;
	protected long when;
	protected int eventType, x, y;
	private AbstractComponent source;
	public CustomMouseEvent(AbstractComponent source, int eventType, long when, int x, int y) {
		this.source = source;
		this.when = when;
		this.eventType = eventType;
		this.x = x;
		this.y = y;
	}
	public AbstractComponent getSource() {
		return source;
	}
	public int getX() {
		return x;
	}
	public int getY() {
		return y;
	}
	public int getEventType() {
		return eventType;
	}
	public long getWhen() {
		return when;
	}
}

interface CoordinateRatiosable {
	public float getXScreenRatio();
	public float getYScreenRatio();
	public void setXScreenRatio(float newValue);
	public void setYScreenRatio(float newValue);
	public double getAbsoluteScreenX();
	public double getAbsoluteScreenY();
	public static final int DYNAMIC_COMPUTE = -1;
}

abstract class ComponentMouseListener {
    public abstract void mouseEntered(CustomMouseEvent e);
    public abstract void mouseExited(CustomMouseEvent e);
    public abstract void mouseClicked(CustomMouseEvent e);
    public abstract void mouseDragged(CustomMouseEvent e);
    public abstract void mouseMoved(CustomMouseEvent e);
    public abstract void mousePressed(CustomMouseEvent e);
    public abstract void mouseReleased(CustomMouseEvent e);
}