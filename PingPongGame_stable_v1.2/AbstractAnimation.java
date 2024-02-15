import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.LinkedList;

import javax.swing.JComponent;

import Helpers.TimerHelpers.TimerAdapter;
import Helpers.TimerHelpers.TimerEvent;
import Helpers.TimerHelpers.TimerListener;

public abstract class AbstractAnimation {
    private static boolean hasBeenInitialised = false;
    private boolean isPaused = false, isActive = false;
    protected long duration;
    //Use a nifty class to time the animations!
    protected TimerClass animationTimer = new TimerClass();
    protected final AbstractAnimation cinst = this;
    private static LinkedList<AbstractAnimation> activeAnimations = new LinkedList<AbstractAnimation>();

    public static LinkedList<AbstractAnimation> getActiveAnimations() {
        return activeAnimations;
    }

    public static void pauseAllAnimations() {
        for (AbstractAnimation animation : activeAnimations) {
            animation.pause();
        }
    }

    public static void resumeAllAnimations() {
        for (AbstractAnimation animation : activeAnimations) {
            animation.resume();
        }
    }

    public static void stopAllAnimations() {
        synchronized (activeAnimations) {
            for (AbstractAnimation animation : activeAnimations) {
                animation.stop();
            }
        }
    }

    public TimerClass.TimerState getTimerState() {
        return this.animationTimer.getTimerState();
    }

    public AbstractAnimation(long duration) {
        AbstractAnimation.hasBeenInitialised = true;
        this.duration = duration;
    }

    /**
     * Starts the animation asynchronously (i.e.: without blocking its thread of invocation for the duration of the animation)
     * @param removeOnCompletion Whether or not to remove the animation on comlpletion
     */
    public final void startAsync(boolean removeOnCompletion) {
        if (this.isActive) {
            throw new IllegalStateException("Cannot start active animation");
        } else {
            this.isActive = true;
            activeAnimations.add(this);
            //Fixed the issues with regards to listener preservation past a timer's stopping through the deleteOnTimerStop attribute, which in this case is redundant
            TimerListener l = new TimerAdapter() {
                @Override
                public void timerFinished(TimerEvent e) {
                    if (removeOnCompletion) {
                        activeAnimations.remove(cinst);
                    }
                    cinst.stop();
                }
                @Override
                public void timerStopped(TimerEvent e) {
                    if (removeOnCompletion) {
                        activeAnimations.remove(cinst);
                    }
                    cinst.stop();
                }
            };
            //No need for deleteOnTimerStop
            l.once = true;
            this.animationTimer.addTimerListener(l);
            this.animationTimer.start(duration);
        }
    }

    /**
     * Starts the animation synchronously, blocking its thread of invocation for the duration of the animation
     * @param removeOnCompletion Whether or not to remove the animation on comlpletion
     */
    public final void start(boolean removeOnCompletion) throws InterruptedException {
        if (this.isActive) {
            throw new IllegalStateException("Cannot start active animation");
        } else {
            this.isActive = true;
            activeAnimations.add(this);
            this.animationTimer.start(duration);
            this.animationTimer.lockUntilCompletion();
            //Check whether the animation has finished or was prematurely stopped
            if (this.isActive) {
                this.stop();
            }
            //Remove from list of active animations if applicable
            if (removeOnCompletion) {
                activeAnimations.remove(cinst);
            }
        }
    }

    //When stopping, DO NOT remove the current animation from the list
    public void stop() {
        if (this.isActive) {
            this.isActive = false;
            this.isPaused = false;
            if (this.animationTimer.getActiveStatus()) {
                this.animationTimer.stop();
            }
        } else {
            throw new IllegalStateException("Cannot stop an inactive animation");
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void pause() {
        this.isPaused = true;
        this.animationTimer.pause();
    }

    public void resume() {
        this.animationTimer.resume();
        this.isPaused = false;
    }

    public void remove() {
        activeAnimations.remove(this);
        this.stop();
    }
    public abstract void paint(Graphics2D g2d, JComponent drawComponent);
}

class CountdownAnimation extends AbstractAnimation {
    public int arcRadius = 200;
    public Color arcColor = Color.BLACK, textColor = Color.BLUE, messageColor = Color.BLACK;
    public static Font defaultFont = new Font("Consolas", Font.PLAIN, 50);
    public Font countdownTextFont = CountdownAnimation.defaultFont;
    private FontMetrics fm;
    private String displayStr;
    public String messageStr = "";
    public CountdownAnimation(long duration) {
        super(duration);
    }

    public void paint(Graphics2D g2d, JComponent component) {
        int theta;
        theta = (int) ((this.animationTimer.getRemainingTime() % 1000) * (36f/100f));
        fm = g2d.getFontMetrics(this.countdownTextFont);
        g2d.setFont(this.countdownTextFont);
        g2d.setColor(this.arcColor);
        g2d.fillArc((component.getWidth() - this.arcRadius)/2, (component.getHeight() - this.arcRadius)/2, arcRadius, arcRadius, 90, theta);
        displayStr = Integer.toString((int) Math.ceil(this.animationTimer.getRemainingTime() / 1000.0f));
        g2d.setColor(this.textColor);
        g2d.drawString(displayStr, (component.getWidth() - fm.stringWidth(displayStr))/2, (component.getHeight() - fm.getAscent())/2);
        g2d.setColor(this.messageColor);
        if (messageStr != null) {
            g2d.drawString(messageStr, (component.getWidth() - fm.stringWidth(messageStr))/2, (component.getHeight() + this.arcRadius)/2 + fm.getAscent() + 10);
        }
    }
}

class SpinTextAnimation extends AbstractAnimation {
    Font font;
    Color color;
    String text;
    double x, y;
    float xScreenRatio, yScreenRatio, rotationRate;
    private int rotation;
    private FontMetrics fm;
    public SpinTextAnimation(long duration, double x, double y, float rotationRate, float xScreenRatio, float yScreenRatio, Color color, Font font, String text) {
        super(duration);
        this.x = x;
        this.y = y;
        this.rotationRate = rotationRate;
        this.xScreenRatio = xScreenRatio;
        this.yScreenRatio = yScreenRatio;
        this.color = color;
        this.font = font;
        this.text = text;
    }
    public void paint(Graphics2D g2d, JComponent component) {
        rotation = (int) (this.animationTimer.getElapsedTime() * rotationRate) % 360;
        g2d.setFont(this.font);
        g2d.setColor(new Color(
            (float) Math.abs(Math.sin(this.animationTimer.getElapsedTime() * this.rotationRate/10)),
            (float) Math.abs(Math.sin(this.animationTimer.getElapsedTime() * this.rotationRate/10 + Math.PI/3)),
            (float) Math.abs(Math.sin(this.animationTimer.getElapsedTime() * this.rotationRate/10 + 2/3 * Math.PI))
        ));
        fm = g2d.getFontMetrics();
        g2d.translate(this.xScreenRatio * component.getWidth() + this.x + fm.stringWidth(this.text)/2, this.yScreenRatio * component.getHeight() + this.y + fm.getAscent()/2);
        g2d.rotate(rotation * (Math.PI/180));
        g2d.drawString(this.text, -fm.stringWidth(this.text)/2, -fm.getAscent()/2);
        g2d.rotate(-rotation * (Math.PI/180));
        g2d.translate(-(this.xScreenRatio * component.getWidth() + this.x + fm.stringWidth(this.text)/2), -(this.yScreenRatio * component.getHeight() + this.y + fm.getAscent()/2));
    }
}