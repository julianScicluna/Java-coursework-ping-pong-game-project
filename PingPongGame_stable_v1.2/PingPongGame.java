import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.FontMetrics;
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowStateListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.AbstractAction;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JColorChooser;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;

import Helpers.AudioHelpers.AudioPlayer;
import Helpers.AudioHelpers.AudioThread;

import javax.swing.event.ChangeEvent;

public class PingPongGame extends JPanel {
	//Code adapted from https://www.w3schools.com/java/java_enums.asp
	public static enum AIModes {
		AI_STUPID,
		AI_TYPICAL,
		AI_VECTOR
	}
	public static enum CompetitionMode {
		FREE_PLAY,
		TIME_COMPETITION,
		TARGET_COMPETITION
	}
	public static class GameSoundEffectManager {
		private PingPongGame instance;
		public static final int GOAL = 1, WALL_IMPACT = 2;
		public GameSoundEffectManager(PingPongGame instance) {
			this.instance = instance;
		}
		public void playGameSound(int sound) {
			this.instance.blockingTasksPool.submit(new Runnable() {
				@Override
				public void run() {
					try {
						switch (sound) {
							case GOAL: {
								instance.scoreSoundPlayer.start(0, Long.MAX_VALUE, new Runnable() {
									@Override
									public void run() {
										((FloatControl) instance.scoreSoundPlayer.getControl(FloatControl.Type.MASTER_GAIN)).setValue((float) (20 * Math.log(instance.gameSoundsVolume/100.0f)/Math.log(10)));
									}
								});
								break;
							}
							case WALL_IMPACT: {
								instance.impactSoundPlayer.start(0, Long.MAX_VALUE, new Runnable() {
									@Override
									public void run() {
										((FloatControl) instance.impactSoundPlayer.getControl(FloatControl.Type.MASTER_GAIN)).setValue((float) (20 * Math.log(instance.gameSoundsVolume/100.0f)/Math.log(10)));
									}
								});
								break;
							}
						}
					} catch (IOException ioe) {
						ioe.printStackTrace();
					} catch (LineUnavailableException lue) {
						lue.printStackTrace();
					} catch (UnsupportedAudioFileException uafe) {
						uafe.printStackTrace();
					}
				}
			});
		}
	}
	//These MUST be of type "byte", NOT enum types
	public static final byte BALL_TRAIL = 0, STAR_TRAIL = 1;
	public static final byte YELLOW_STAR = 0, GREEN_STAR = 1, BLUE_STAR = 2;
	public static BufferedImage yellowStar, greenStar, blueStar;
	byte ballTrailType = BALL_TRAIL, gameBgAudioVolume = 100, gameSoundsVolume = 100;
	ExecutorService blockingTasksPool = Executors.newFixedThreadPool(3);
	//numPauseReasons should only be changed from ONE thread (The EDT) or synchronized
	int fps = 60, maxPlayers = 2, ballRadius = 10, ballTrailAmount = 3, scoreTarget = 5, gameTimeLimit = 60, butterflyImageCachedX = 0, butterflyImageCachedY = 0;
	CompetitionMode competitionMode = CompetitionMode.FREE_PLAY;
	AIModes gameAIIntelligenceLevel = AIModes.AI_TYPICAL;
	public static Font defaultFont = new Font("Consolas", Font.PLAIN, 20);
	public double gravity = 9.8, ballSpeed, playerSpeed, ballXComponentLowerBound = 2.0;
	public boolean playingWithAI, randomBallDeflection, quadraticBallMotion = false, autoResetScene = true, doBallTrailEffect = false, doRandomTrailPositions = false, doTrailParticleShrinking = false, finalGameAction = false, doBgAudio = false, doGameSounds = false, doingButterfly = false;
	private Image butterflyImage;
	public Color countdownMessageColor;
	public Player AIPlayer;
	public Oval gameBall;
	TimerClass gameTimer = new TimerClass();
	final PingPongGame cinst = this;
	JFrame frame;
	JButton pauseBtn;
	Counter gameObjectiveCounter = new Counter(0, 50, 0f, 0.5f, 0, "", "", Color.BLACK, new Font("Consolas", Font.PLAIN, 20));
	String pausedMessage = "Game Paused!", countdownMessage = "";
	private Thread physicsThread, graphicsThread, playerThread;
	private AudioThread audioThread;
	private ExecutorService audioService = Executors.newFixedThreadPool(2);
	private WindowListener currentWindowListener;
	private boolean paused = false, hasDeclaredVictor, lockAllObjects = false, restarting = false, resettingScene = false, resuming = false;
	private int[] ballTrailCoords;
	private Button lastGoalButton, restartButton;
	private MenuResponseStates msgboxState;
	private JFrame tempFrame;
	private long nextButterflyTime /*WARNING! Used in ALL difficulties!*/, latestApplicableGameSeizeTimestamp /*The time when the game was last paused/nearly restarted. Used when computing the remaining time to display a butterfly*/;
	private final AudioPlayer audioPlayer = new AudioPlayer();
	private final GameSoundEffectManager gameSounds = new GameSoundEffectManager(this);
	private AudioPlayer impactSoundPlayer, scoreSoundPlayer;
	public static void main(String[] args) {
		PingPongGame game = new PingPongGame();
	}
	public void stopEverything() {
		//Frame disposal (Window closing) MUST go first for good UX - The frame should close immediately after clicking the close button, not after all the other resources have been disposed of
		if (tempFrame != null) {
			tempFrame.dispose();
		}
		frame.dispose();
		if (physicsThread != null) {
			physicsThread.interrupt();
		}
		if (playerThread != null) {
			playerThread.interrupt();
		}
		if (graphicsThread != null) {
			graphicsThread.interrupt();
		}

		//The audio thread is ALWAYS started, regardless of whether audio was initially enabled, due to the possibility of it being enabled/disabled later in gameplay
		if (audioThread != null) {
			audioThread.interrupt();
		}
		audioService.shutdownNow();
		//DO NOT stop the audioPlayer - it would have been previously stopped by the audioThread.interrupt() method invocation
		/*if (cinst.doBgAudio) {
			try {
				if (audioPlayer.isActive()) {
					audioPlayer.stop();
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}*/
		//Do not stop() the players, they are probably already inactive. If that is not the case, the tracks they play are extrmely short (at most ~1 second).
		if (impactSoundPlayer != null) {
			impactSoundPlayer.clearAudioFile();
		}
		if (scoreSoundPlayer != null) {
			scoreSoundPlayer.clearAudioFile();
		}
		TimerClass.stopTimerServiceNow();
		AbstractComponent.componentEventDispatchThread.interruptNow();
		blockingTasksPool.shutdownNow();
		JAnimation.stopAnimationServiceNow();
	}
	public void displayButterfly() {
		this.doingButterfly = true;
		this.butterflyImageCachedX = 0;
		this.butterflyImageCachedY = (int) (Math.random() * (this.getHeight() - butterflyImage.getHeight()));
		this.butterflyImage.setX(this.butterflyImageCachedX);
		this.butterflyImage.setY(this.butterflyImageCachedY);
		synchronized(butterflyImage) {
			butterflyImage.setVisible(true);
		}
	}
	public void createPlayerRepresentativeComponent(TemporaryPlayer newPlayerTemplate, JComponent parentComponent, JButton newPlayer, boolean playWithAI) {
		JPanel playerTemplatePanel = new JPanel();
		playerTemplatePanel.setBorder(BorderFactory.createLineBorder(Color.GREEN));
		JLabel playerDescriptor = new JLabel(newPlayerTemplate.name);
		playerDescriptor.setForeground(newPlayerTemplate.color);
		playerTemplatePanel.add(playerDescriptor);
		JButton editPlayer = new JButton("Edit...");
		editPlayer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				blockingTasksPool.submit(new Runnable() {
					public void run() {
						try {
							TemporaryPlayer editedPlayerTemplate = showCreatePlayerDialog(newPlayerTemplate.name, newPlayerTemplate.color, newPlayerTemplate.bindings);
							playerDescriptor.setText(editedPlayerTemplate.name);
							playerDescriptor.setForeground(editedPlayerTemplate.color);
							newPlayerTemplate.name = editedPlayerTemplate.name;
							newPlayerTemplate.color = editedPlayerTemplate.color;
							newPlayerTemplate.bindings = editedPlayerTemplate.bindings;
							editedPlayerTemplate.remove();
							parentComponent.revalidate();
							parentComponent.repaint();
						} catch (InterruptedException ie) {
							ie.printStackTrace();
						}
					}
				});
			}
		});
		playerTemplatePanel.add(editPlayer);
		JButton removePlayer = new JButton("X");
		removePlayer.setForeground(Color.WHITE);
		removePlayer.setBackground(Color.RED);
		removePlayer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				blockingTasksPool.submit(new Runnable() {
					public void run() {
						if (JOptionPane.showConfirmDialog(frame, "Are you sure you want to delete this player?", "Delete player...", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
							parentComponent.remove(playerTemplatePanel);
							newPlayerTemplate.remove();
							if ((parentComponent.getComponents().length - 1 < maxPlayers) || (parentComponent.getComponents().length < maxPlayers && playWithAI)) {
								newPlayer.setEnabled(true);
							}
							parentComponent.revalidate();
							parentComponent.repaint();
						}
					}
				});
			}
		});
		//playersListContent
		playerTemplatePanel.add(removePlayer);
		newPlayerTemplate.indicatorPanel = playerTemplatePanel;
		parentComponent.add(playerTemplatePanel);
		parentComponent.revalidate();
		parentComponent.repaint();
	}

	public TemporaryPlayer showCreatePlayerDialog(String name, Color color, HashMap<KeyStroke, Integer> existingBindingsMap) throws InterruptedException {
		if (tempFrame == null) {
			tempFrame = new JFrame();
		} else {
			JOptionPane.showMessageDialog(frame, "Sorry, a window is already open. Please close it before opening another one!");
			throw new IllegalStateException("Window already open");
		}
		msgboxState = MenuResponseStates.PENDING_RESPONSE;
		tempFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		tempFrame.addWindowListener(new WindowListener() {
			public void windowDeactivated(WindowEvent e) {
			}
			public void windowActivated(WindowEvent e) {
			}
			public void windowDeiconified(WindowEvent e) {
			}
			public void windowIconified(WindowEvent e) {
			}
			public void windowClosed(WindowEvent e) {
			}
			public void windowClosing(WindowEvent e) {
				msgboxState = MenuResponseStates.CANCEL;
			}
			public void windowOpened(WindowEvent e) {
			}
		});
		tempFrame.setTitle("Create new player...");
		tempFrame.setSize(new Dimension(500, 500));
		JScrollPane scrollPane = new JScrollPane();
		JPanel panel = new JPanel();
		scrollPane.add(panel);
		scrollPane.setViewportView(panel);
		panel.setLayout(new GridBagLayout());
		tempFrame.getContentPane().add(scrollPane);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 0;
		JLabel greeting = new JLabel("Let's get your character set up!");
		panel.add(greeting, c);
		
		JPanel playerNamePanel = new JPanel();
		playerNamePanel.setLayout(new FlowLayout());
		c.gridy = 1;
		panel.add(playerNamePanel, c);
		
		JLabel playerNameLabel = new JLabel("Enter the player's name here:");
		playerNamePanel.add(playerNameLabel);
		JTextField playerNameInput = new JTextField();
		playerNameInput.setPreferredSize(new Dimension(100, 20));
		playerNameInput.setText(name);
		playerNamePanel.add(playerNameInput);
		
		JPanel playerColorPanel = new JPanel();
		c.gridy = 2;
		panel.add(playerColorPanel, c);
		
		JLabel playerColorLabel = new JLabel("Select the player's colour!");
		playerColorPanel.add(playerColorLabel);
		JColorChooser playerColorInput = new JColorChooser(color);
		playerColorPanel.add(playerColorInput);
		
		JPanel playerKeyBindingsPanel = new JPanel();
		c.gridy = 3;
		panel.add(playerKeyBindingsPanel, c);
		
		JLabel playerKeyBindingsLabel = new JLabel("Bind keys to a player action!");
		playerKeyBindingsPanel.add(playerKeyBindingsLabel); //Check - experimental
		JScrollPane playerKeyBindingsList = new JScrollPane();
		playerKeyBindingsList.setPreferredSize(new Dimension(200, 150));
		playerKeyBindingsPanel.add(playerKeyBindingsList);
		JPanel keyBindingsListContent = new JPanel();
		keyBindingsListContent.setLayout(new BoxLayout(keyBindingsListContent, BoxLayout.Y_AXIS));
		playerKeyBindingsList.add(keyBindingsListContent);
		playerKeyBindingsList.setViewportView(keyBindingsListContent);
		JButton addKeyBinding = new JButton("Add a key binding!");
		addKeyBinding.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Handling a click event to create a new key binding on the user's demand
				JPanel keyBinding = new JPanel();
				keyBinding.setBorder(BorderFactory.createLineBorder(Color.BLUE));
				
				JComboBox<ComboItem<KeyStroke>> inputKey = new JComboBox<ComboItem<KeyStroke>>();
				inputKey.addItem(new ComboItem<KeyStroke>("Up key", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("Down key", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("Left key", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("Right key", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("q key", KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("w key", KeyStroke.getKeyStroke(KeyEvent.VK_W, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("e key", KeyStroke.getKeyStroke(KeyEvent.VK_E, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("r key", KeyStroke.getKeyStroke(KeyEvent.VK_R, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("t key", KeyStroke.getKeyStroke(KeyEvent.VK_T, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("y key", KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("u key", KeyStroke.getKeyStroke(KeyEvent.VK_U, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("i key", KeyStroke.getKeyStroke(KeyEvent.VK_I, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("o key", KeyStroke.getKeyStroke(KeyEvent.VK_O, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("p key", KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("a key", KeyStroke.getKeyStroke(KeyEvent.VK_A, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("s key", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("d key", KeyStroke.getKeyStroke(KeyEvent.VK_D, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("f key", KeyStroke.getKeyStroke(KeyEvent.VK_F, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("g key", KeyStroke.getKeyStroke(KeyEvent.VK_G, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("h key", KeyStroke.getKeyStroke(KeyEvent.VK_H, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("j key", KeyStroke.getKeyStroke(KeyEvent.VK_J, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("k key", KeyStroke.getKeyStroke(KeyEvent.VK_K, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("l key", KeyStroke.getKeyStroke(KeyEvent.VK_L, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("z key", KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("x key", KeyStroke.getKeyStroke(KeyEvent.VK_X, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("c key", KeyStroke.getKeyStroke(KeyEvent.VK_C, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("v key", KeyStroke.getKeyStroke(KeyEvent.VK_V, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("b key", KeyStroke.getKeyStroke(KeyEvent.VK_B, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("n key", KeyStroke.getKeyStroke(KeyEvent.VK_N, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("m key", KeyStroke.getKeyStroke(KeyEvent.VK_M, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("1 key", KeyStroke.getKeyStroke(KeyEvent.VK_1, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("2 key", KeyStroke.getKeyStroke(KeyEvent.VK_2, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("3 key", KeyStroke.getKeyStroke(KeyEvent.VK_3, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("4 key", KeyStroke.getKeyStroke(KeyEvent.VK_4, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("5 key", KeyStroke.getKeyStroke(KeyEvent.VK_5, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("6 key", KeyStroke.getKeyStroke(KeyEvent.VK_6, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("7 key", KeyStroke.getKeyStroke(KeyEvent.VK_7, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("8 key", KeyStroke.getKeyStroke(KeyEvent.VK_8, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("9 key", KeyStroke.getKeyStroke(KeyEvent.VK_9, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("0 key", KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)));
				keyBinding.add(inputKey);
				
				JComboBox<ComboItem<Integer>> outputAction = new JComboBox<ComboItem<Integer>>();
				outputAction.addItem(new ComboItem<Integer>("Move up", Player.UP));
				outputAction.addItem(new ComboItem<Integer>("Move down", Player.DOWN));
				keyBinding.add(outputAction);
				
				JButton delete = new JButton("X");
				delete.setForeground(new Color(255, 255, 255, 255));
				delete.setBackground(new Color(255, 0, 0, 255));
				delete.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						keyBindingsListContent.remove(keyBinding);
						keyBindingsListContent.revalidate();
						keyBindingsListContent.repaint();
					}
				});
				keyBinding.add(delete);
				keyBindingsListContent.add(keyBinding, 1);
				keyBindingsListContent.revalidate();
				keyBindingsListContent.repaint();
			}
		});
		keyBindingsListContent.add(addKeyBinding);
		
		if (existingBindingsMap != null) {
			//Iteratively adding any prespecified key bindings (in parameter existingBindingsMap)
			for (KeyStroke s : existingBindingsMap.keySet()) {
				JPanel keyBinding = new JPanel();
				keyBinding.setBorder(BorderFactory.createLineBorder(Color.BLUE));
				
				JComboBox<ComboItem<KeyStroke>> inputKey = new JComboBox<ComboItem<KeyStroke>>();
				inputKey.addItem(new ComboItem<KeyStroke>("Up key", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("Down key", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("Left key", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("Right key", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("q key", KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("w key", KeyStroke.getKeyStroke(KeyEvent.VK_W, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("e key", KeyStroke.getKeyStroke(KeyEvent.VK_E, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("r key", KeyStroke.getKeyStroke(KeyEvent.VK_R, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("t key", KeyStroke.getKeyStroke(KeyEvent.VK_T, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("y key", KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("u key", KeyStroke.getKeyStroke(KeyEvent.VK_U, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("i key", KeyStroke.getKeyStroke(KeyEvent.VK_I, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("o key", KeyStroke.getKeyStroke(KeyEvent.VK_O, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("p key", KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("a key", KeyStroke.getKeyStroke(KeyEvent.VK_A, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("s key", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("d key", KeyStroke.getKeyStroke(KeyEvent.VK_D, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("f key", KeyStroke.getKeyStroke(KeyEvent.VK_F, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("g key", KeyStroke.getKeyStroke(KeyEvent.VK_G, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("h key", KeyStroke.getKeyStroke(KeyEvent.VK_H, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("j key", KeyStroke.getKeyStroke(KeyEvent.VK_J, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("k key", KeyStroke.getKeyStroke(KeyEvent.VK_K, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("l key", KeyStroke.getKeyStroke(KeyEvent.VK_L, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("z key", KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("x key", KeyStroke.getKeyStroke(KeyEvent.VK_X, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("c key", KeyStroke.getKeyStroke(KeyEvent.VK_C, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("v key", KeyStroke.getKeyStroke(KeyEvent.VK_V, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("b key", KeyStroke.getKeyStroke(KeyEvent.VK_B, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("n key", KeyStroke.getKeyStroke(KeyEvent.VK_N, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("m key", KeyStroke.getKeyStroke(KeyEvent.VK_M, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("1 key", KeyStroke.getKeyStroke(KeyEvent.VK_1, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("2 key", KeyStroke.getKeyStroke(KeyEvent.VK_2, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("3 key", KeyStroke.getKeyStroke(KeyEvent.VK_3, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("4 key", KeyStroke.getKeyStroke(KeyEvent.VK_4, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("5 key", KeyStroke.getKeyStroke(KeyEvent.VK_5, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("6 key", KeyStroke.getKeyStroke(KeyEvent.VK_6, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("7 key", KeyStroke.getKeyStroke(KeyEvent.VK_7, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("8 key", KeyStroke.getKeyStroke(KeyEvent.VK_8, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("9 key", KeyStroke.getKeyStroke(KeyEvent.VK_9, 0)));
				inputKey.addItem(new ComboItem<KeyStroke>("0 key", KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)));
				inputKey.setSelectedItem(new ComboItem<KeyStroke>(null, s));
				keyBinding.add(inputKey);

				JComboBox<ComboItem<Integer>> outputAction = new JComboBox<ComboItem<Integer>>();
				outputAction.addItem(new ComboItem<Integer>("Move up", Player.UP));
				outputAction.addItem(new ComboItem<Integer>("Move down", Player.DOWN));
				outputAction.setSelectedItem(new ComboItem<Integer>(null, existingBindingsMap.get(s)));
				keyBinding.add(outputAction);
				
				
				JButton delete = new JButton("X");
				delete.setForeground(new Color(255, 255, 255, 255));
				delete.setBackground(new Color(255, 0, 0, 255));
				delete.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						keyBindingsListContent.remove(keyBinding);
						keyBindingsListContent.revalidate();
						keyBindingsListContent.repaint();
					}
				});
				keyBinding.add(delete);
				keyBindingsListContent.add(keyBinding, 1);
				keyBindingsListContent.revalidate();
				keyBindingsListContent.repaint();
			}
		}

		JPanel OKCancelPanel = new JPanel();
		c.gridx = 2;
		c.gridy = 4;
		panel.add(OKCancelPanel, c);
				
		JButton OKButton = new JButton("OK");
		OKButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				msgboxState = MenuResponseStates.OK;
			}
		});
		OKCancelPanel.add(OKButton);
		JButton CancelButton = new JButton("Cancel");
		CancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				msgboxState = MenuResponseStates.CANCEL;
			}
		});
		OKCancelPanel.add(CancelButton);
		
		tempFrame.setVisible(true);
		while (msgboxState == MenuResponseStates.PENDING_RESPONSE) {
			Thread.sleep(50);
		}
		tempFrame.dispose();
		tempFrame = null;
		if (msgboxState == MenuResponseStates.OK) {
			HashMap<KeyStroke, Integer> bindingMap = new HashMap<KeyStroke, Integer>();
			java.awt.Component[] bindingComponents = keyBindingsListContent.getComponents();
			JPanel bindingPanel;
			for (int i = 1; i < bindingComponents.length; i++) {
				bindingPanel = (JPanel) bindingComponents[i];
				java.awt.Component[] bindingComponentChildren = bindingPanel.getComponents();
				bindingMap.put(((ComboItem<KeyStroke>) ((JComboBox<?>) bindingComponentChildren[0]).getSelectedItem()).value, ((ComboItem<Integer>) ((JComboBox<?>) bindingComponentChildren[1]).getSelectedItem()).value);
			}
			return new TemporaryPlayer(playerNameInput.getText(), bindingMap, playerColorInput.getColor(), this);
		} else if (msgboxState == MenuResponseStates.CANCEL) {
			throw new SecurityException("Operation cancelled by user");
		}
		/*HashMap<String, JComponent> compsMap = new HashMap<String, JComponent>();
		compsMap.put("name", playerNameInput);
		compsMap.put("color", playerColorInput);
		compsMap.put("keybindings", keyBindingsListContent);
		compsMap.put("ok", OKButton);
		return compsMap;*/
		return null;
	}
	public void displayAdvancedSettings() {
		//Maximum one frame at the same time
		if (tempFrame == null) {
			//Create a new JFrame for advanced settings
			tempFrame = new JFrame();
		} else {
			JOptionPane.showMessageDialog(frame, "Sorry, a window is already open. Please close it before opening another one!");
			throw new IllegalStateException("Window already open");
		}
		//Set state to 'waiting' for response
		msgboxState = MenuResponseStates.PENDING_RESPONSE;
		//Set frame title - advanced settings
		tempFrame.setTitle("Advanced Game Settings");
		//Set frame size to 500*500px
		tempFrame.setSize(500, 500);
		//When the user tries to close the window (presses 'X'), do nothing. It will be handled by the window listener
		tempFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		//Window listener to handle window events (e.g.: close)
		//Create an anonymous inner class of WindowListener interface (override all (effectively) abstract methods)
		tempFrame.addWindowListener(new WindowListener() {
			public void windowDeactivated(WindowEvent e) {
			}
			public void windowActivated(WindowEvent e) {
			}
			public void windowDeiconified(WindowEvent e) {
			}
			public void windowIconified(WindowEvent e) {
			}
			public void windowClosed(WindowEvent e) {
			}
			public void windowClosing(WindowEvent e) {
				//If the window is about to close, set the state indicating premature termination (abort) of the operation
				msgboxState = MenuResponseStates.CANCEL;
			}
			public void windowOpened(WindowEvent e) {
			}
		});
		
		//A Panel to store the Advanced Settings panel's contents
		JPanel mainPanel = new JPanel();
		//Use a GridBagLayout for fairly fine control over element positioning and space allocation ratios
		mainPanel.setLayout(new GridBagLayout());
		//Add the panel to the frame's content pane
		tempFrame.getContentPane().add(mainPanel);
		
		//Create a GridBagConstraints object to dictate the placement of JComponents
		GridBagConstraints c = new GridBagConstraints();
		//Specify the component's position (1, 1) on the element grid, so to speak
		c.gridx = 1;
		c.gridy = 1;
		
		//A JPanel storing the UI regarding the ball, particularly its motion
		JPanel ballSettings = new JPanel();
		//Give this JPanel a BoxLayout, wherein elements are vertically aligned
		ballSettings.setLayout(new BoxLayout(ballSettings, BoxLayout.PAGE_AXIS));
		mainPanel.add(ballSettings, c);
		
		//A checkbox to toggle whether the ball should be influenced by gravity
		JCheckBox quadraticMotion = new JCheckBox("Quadratic ball motion (bouncy-bounce):");
		//Set its selected state to the variable's value
		quadraticMotion.setSelected(quadraticBallMotion);
		ballSettings.add(quadraticMotion);
		//DO NOT add a state change listener which changes the value, due to the possibility of termination, in which case changes are not saved
		
		//Premature declaration of a text field accepting the lower bound for the ball motion X component for access to the reference in anonymous inner classes requiring it, primarily for enabling/disabling the field
		JTextField ballXVelocityLowerBound = new JTextField();

		//Safety feature: If the ball's velocity's x-component does not meet lower bounds, the scene is reset; the ball is repositioned to the middle and the ping-pong sliders are locked for 3 seconds (countdown). The ball's motion would also be redefined
		JCheckBox ballStuckResetScene = new JCheckBox("Automatically reset scene if ball is stuck:");
		//Enable/disable any related fields on state change based on the current state
		ballStuckResetScene.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				ballXVelocityLowerBound.setEnabled(ballStuckResetScene.isSelected());
			}
		});
		ballStuckResetScene.setSelected(autoResetScene);
		ballSettings.add(ballStuckResetScene);
		
		//JPanel to store the label and input componet associated with ball x-component velocity lower bounds
		JPanel velocityBoundsPanel = new JPanel();
		ballSettings.add(velocityBoundsPanel);
		
		//JLabel to inform user of field's purpose (in this case ball x component)
		JLabel XLowerBoundVelocityLabel = new JLabel("Minimum ball X-component to reset scene (somewhat low or zero positive value recommended)");
		velocityBoundsPanel.add(XLowerBoundVelocityLabel);
		ballXVelocityLowerBound.setText(Double.toString(ballXComponentLowerBound));
		ballXVelocityLowerBound.setPreferredSize(new Dimension(50, 20));
		velocityBoundsPanel.add(ballXVelocityLowerBound);

		//JPanel storing controls for special effects such as ball motion blurs and trailing stars
		JPanel effectsPanel = new JPanel();
		effectsPanel.setLayout(new BoxLayout(effectsPanel, BoxLayout.PAGE_AXIS));
		c.gridy = 2;
		mainPanel.add(effectsPanel, c);

		//A JPanel storing the number of trailing balls behind the original ball
		JPanel ballTrailCountPanel = new JPanel();

		//JLabel to specify the purpose of the objectTrailCountTextField
		JLabel ballTrailCountLabel = new JLabel("Number of objects trailing behind original:");
		ballTrailCountPanel.add(ballTrailCountLabel);

		//JTextField to accept a number as user input
		JTextField objectTrailCountTextField = new JTextField();
		objectTrailCountTextField.setText(Integer.toString(ballTrailAmount));
		objectTrailCountTextField.setPreferredSize(new Dimension(50, 20));
		ballTrailCountPanel.add(objectTrailCountTextField);

		//JCheckbox to toggle the randomisation of trail particles' position. Applicable only to star trail mode
		JCheckBox randomisedTrailCheckBox = new JCheckBox("Randomise trail particles' position: ");
		randomisedTrailCheckBox.setSelected(doRandomTrailPositions);

		//JCheckbox to toggle the shrinking of trailing particles with age
		JCheckBox shrinkTrailParticlesCheckBox = new JCheckBox("Shrink trail particles as they age: ");
		shrinkTrailParticlesCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Singular '&' character intentional - short-circuit 'if' statement to avoid unnecessarily querying the user
				if (shrinkTrailParticlesCheckBox.isSelected()) {
					//Display performance warning
					if (JOptionPane.showConfirmDialog(tempFrame, "Dynamically shrinking each and every particle during gameplay is somewhat resource-intensive and can possibly result in poor game performance and unpleasant problems, such as a noticeable frame rate drop. Are you sure you want to continue?", "PERFORMANCE WARNING: Dynamically shrinking particles", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION) {
						//If the user does not want to continue, reset the state to false. This listener is invoked after state change
						shrinkTrailParticlesCheckBox.setSelected(false);
					}
				}
			}
		});
		shrinkTrailParticlesCheckBox.setSelected(doTrailParticleShrinking);

		//A JComboBox with options of type ComboItem (which stores values of type Byte (Wrapper class of primitive byte (primitives cannot be used in generics in java))) to represent the different types of trail (ball trail, star trail)
		JComboBox<ComboItem<Byte>> trailTypeSelector = new JComboBox<ComboItem<Byte>>();
		trailTypeSelector.addItem(new ComboItem<Byte>("Standard ball trail", BALL_TRAIL));
		trailTypeSelector.addItem(new ComboItem<Byte>("Star trail", STAR_TRAIL));
		//ComboItem<T> equals() and hashCode() methods are overridden, to return the equality/hashcode of their values, respectively. Thus, a null value for the text field would make no difference in hash code/equality between two ComboItem<T> instances
		trailTypeSelector.setSelectedItem(new ComboItem<Byte>(null, ballTrailType));
		//Certain options are exclusive to star trail type. Thus, enable/disable them (grey them out) based on the trail type
		ActionListener trailTypeSelectorChangeHandler = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				randomisedTrailCheckBox.setEnabled(((ComboItem<Byte>) (trailTypeSelector.getSelectedItem())).value.equals(STAR_TRAIL));
			}
		};
		trailTypeSelector.addActionListener(trailTypeSelectorChangeHandler);
		trailTypeSelectorChangeHandler.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));

		JCheckBox trailBlurSelectorCheckbox = new JCheckBox("Enable ball trail effect (WARNING: CAN CAUSE PERFORMANCE DROP)");
		trailBlurSelectorCheckbox.setSelected(doBallTrailEffect);
		ChangeListener blurSelectorListener = new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				randomisedTrailCheckBox.setEnabled(trailBlurSelectorCheckbox.isSelected() && !((ComboItem<Byte>) (trailTypeSelector.getSelectedItem())).value.equals(BALL_TRAIL));
				shrinkTrailParticlesCheckBox.setEnabled(trailBlurSelectorCheckbox.isSelected());
				objectTrailCountTextField.setEnabled(trailBlurSelectorCheckbox.isSelected());
				trailTypeSelector.setEnabled(trailBlurSelectorCheckbox.isSelected());
			}
		};
		trailBlurSelectorCheckbox.addChangeListener(blurSelectorListener);
		effectsPanel.add(trailBlurSelectorCheckbox);
		effectsPanel.add(ballTrailCountPanel);
		//Trigger change listener for the first time
		blurSelectorListener.stateChanged(new ChangeEvent(new Object()));

		JPanel trailPropertiesPanel = new JPanel();

		JLabel trailTypeLabel = new JLabel("Select type of trail behind the ball: ");
		trailPropertiesPanel.add(trailTypeLabel);
		trailPropertiesPanel.add(trailTypeSelector);
		trailPropertiesPanel.add(randomisedTrailCheckBox);
		trailPropertiesPanel.add(shrinkTrailParticlesCheckBox);

		effectsPanel.add(trailPropertiesPanel);

		JPanel OKCancelPanel = new JPanel();
		c.gridx = 1;
		c.gridy = 3;
		mainPanel.add(OKCancelPanel, c);
				
		JButton OKButton = new JButton("OK");
		OKButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ballStuckResetScene.isSelected()) {
					try {
						double lboundValue = Double.parseDouble(ballXVelocityLowerBound.getText());
						if (lboundValue < 0 || lboundValue == Double.POSITIVE_INFINITY || Double.isNaN(lboundValue)) {
							JOptionPane.showMessageDialog(tempFrame, "The lower bound for the ball's X component MUST be a FINITE and POSITIVE number LOWER THAN THE BALL'S SPEED! (Decimal numbers allowed)");
							return;
						} else {
							if (lboundValue > 5.0) {
								if (JOptionPane.showConfirmDialog(tempFrame, "The lower bound of x-velocity you have specified is quite high. While your input is perfectly logical, it can result in possibly excessive and unnecessary scene resetting. Are you sure you want to continue?", "WARNING: High x-component lower bound", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
									return;
								}
							}
						}
					} catch (NumberFormatException nfe) {
						JOptionPane.showMessageDialog(tempFrame, "The lower bound for the ball's X component MUST be a finite and positive NUMBER! (Decimal numbers allowed)");
						return;
					}
				}
				if (trailBlurSelectorCheckbox.isSelected()) {
					try {
						int numTrailingObjects = Integer.parseInt(objectTrailCountTextField.getText());
						if (numTrailingObjects < 0 || numTrailingObjects == Double.POSITIVE_INFINITY || Double.isNaN(numTrailingObjects)) {
							JOptionPane.showMessageDialog(tempFrame, "The amount of balls in the trail MUST be a FINITE and POSITIVE integer, preferably above zero! (Decimal numbers NOT allowed)");
							return;
						} else if (numTrailingObjects > 100) {
							if (JOptionPane.showConfirmDialog(tempFrame, "The number of trailing objects you desire (" + numTrailingObjects + ") is very high. This can possibly result in various performance issues during gameplay, such low game FPS. Are you sure you want to continue?", "PERFORMANCE WARNING: High number of trailing balls", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
								return;
							}
						}
					} catch (NumberFormatException nfe) {
						JOptionPane.showMessageDialog(tempFrame, "The amount of objects in the trail MUST be a finite and positive INTEGER! (Decimal numbers NOT allowed)");
						return;
					}
				}
				msgboxState = MenuResponseStates.OK;
			}
		});
		OKCancelPanel.add(OKButton);
		JButton CancelButton = new JButton("Cancel");
		CancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				msgboxState = MenuResponseStates.CANCEL;
			}
		});
		OKCancelPanel.add(CancelButton);


		tempFrame.pack();
		tempFrame.setVisible(true);
		while (msgboxState == MenuResponseStates.PENDING_RESPONSE) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
		tempFrame.dispose();
		tempFrame = null;
		if (msgboxState == MenuResponseStates.OK) {
			quadraticBallMotion = quadraticMotion.isSelected();
			autoResetScene = ballStuckResetScene.isSelected();
			ballXComponentLowerBound = Double.parseDouble(ballXVelocityLowerBound.getText());
			doBallTrailEffect = trailBlurSelectorCheckbox.isSelected();
			ballTrailAmount = Integer.parseInt(objectTrailCountTextField.getText());
			ballTrailType = ((ComboItem<Byte>) trailTypeSelector.getSelectedItem()).value;
			doRandomTrailPositions = randomisedTrailCheckBox.isSelected();
			doTrailParticleShrinking = shrinkTrailParticlesCheckBox.isSelected();
		} else if (msgboxState == MenuResponseStates.CANCEL) {
			throw new SecurityException("Operation cancelled by user");
		}
	}

	public boolean displaySplashScreen(long duration) {
		//ALL Swing GUI code taken from https://docs.oracle.com/javase/tutorial/uiswing/components/index.html and https://docs.oracle.com/javase/tutorial/uiswing/components/componentlist.html
		//ALL Swing event listener and dispatcher code taken from https://docs.oracle.com/javase/tutorial/uiswing/events/eventsandcomponents.html
		frame = new JFrame();
		//Set the window's preferred size
		frame.setSize(500, 500);
		//Set the window's title
		frame.setTitle("Setting up Ping-Pong!");
		//Disable default action by the EDT on close
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		//Get thread invoking this method
		Thread currentThread = Thread.currentThread();
		//Add listener to dispose of the window along with all other resources on close
		currentWindowListener = new WindowListener() {
			public void windowDeactivated(WindowEvent e) {
			}
			public void windowActivated(WindowEvent e) {
			}
			public void windowDeiconified(WindowEvent e) {
			}
			public void windowIconified(WindowEvent e) {
			}
			public void windowClosed(WindowEvent e) {
			}
			public void windowClosing(WindowEvent e) {
				//Stop the thread which created this event listener, DO NOT use Thread.currentThread() here; will refer to the AWT event thread!
				currentThread.interrupt();
				//Free all resources
				stopEverything();
			}
			public void windowOpened(WindowEvent e) {
			}
		};
		frame.addWindowListener(currentWindowListener);

		//Create JPanel to store all the splash screen's contents and add it to the JFrame's content pane
		JPanel mainDisplayPanel = new JPanel();
		mainDisplayPanel.setLayout(new GridBagLayout());
		frame.getContentPane().add(mainDisplayPanel);

		//A label to contextualise the game
		JLabel titleLabel = new JLabel("Just setting up ping-pong...");
		titleLabel.setForeground(Color.RED);
		titleLabel.setFont(new Font("Algerian", Font.PLAIN, 30));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 1;
		mainDisplayPanel.add(titleLabel, gbc);

		//A label to soothe and excite the user - it is a home screen after all!
		JLabel soothingLabel = new JLabel("Prepare to feel relaxed!");
		soothingLabel.setForeground(Color.GREEN);
		soothingLabel.setFont(defaultFont);
		gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 2;
		mainDisplayPanel.add(soothingLabel, gbc);

		//Create a new animation for ping-pong splash screen
		JAnimation<BallAnimationState> animation = new JAnimation<BallAnimationState>() {
			private final double[] ballDimensions = new double[] {ballRadius * 2, ballRadius * 2};
			@Override
			public BallAnimationState animationFunction(double animationProgress) {
				BallAnimationState bas = new BallAnimationState();
				//Change the BallAnimationState instance's data according to a set of functions, dependent on the animationProgress
				//Set appropriate slider dimensions
				bas.sliderDimensions[0] = 100;
				bas.sliderDimensions[1] = 20;

				bas.slider1Coords[0] = (this.getWidth() - bas.sliderDimensions[0])/2;
				bas.slider1Coords[1] = this.getHeight() - bas.sliderDimensions[1] - 5;

				//Set the ball's position based on the animation progress (UUURRRGGGHHH!!!)
				bas.ballCoords[0] = bas.slider1Coords[0] + (bas.sliderDimensions[0] - ballDimensions[0])/2;
				bas.ballCoords[1] = bas.slider1Coords[1] - ballDimensions[1] - getAnimationDuration()/10 + Math.pow(animationProgress - getAnimationDuration()/2, 2)/Math.pow(getAnimationDuration()/2, 2) * getAnimationDuration()/10;

				return bas;
			}
			@Override
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				BallAnimationState state = animationFunction(this.getAnimationElapsedTime());
				Graphics2D g2d = (Graphics2D) g;
				g2d.setColor(new Color(0, 0, 255));
				g2d.fillRect((int) state.slider1Coords[0], (int) state.slider1Coords[1], (int) state.sliderDimensions[0], (int) state.sliderDimensions[1]);
				g2d.setColor(Color.RED);
				g2d.fillOval((int) state.ballCoords[0], (int) state.ballCoords[1], (int) ballDimensions[0], (int) ballDimensions[1]);
			}
		};
		animation.setBorder(javax.swing.BorderFactory.createLineBorder(Color.BLUE));
		//Position the animation
		gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 3;
		//Allocate all available space to the grid
		gbc.weightx = 1.0f;
		gbc.weighty = 1.0f;
		//Make the JAnimation take all the available space in both dimensions
		gbc.fill = GridBagConstraints.BOTH;
		//Add the JAnimation, following constraints
		mainDisplayPanel.add(animation, gbc);
		//Make the frame visible
		frame.setVisible(true);

		//Get the current time as the start time
		long startTime = System.currentTimeMillis();

		//Start the animation and loop it until thread interruption or time up
		try {
			while (Thread.currentThread().isInterrupted() || (duration != -1 && System.currentTimeMillis() - startTime < duration)) {
				for (int i = 0; i < 5; i++) {
					//Half the height of the previous bounce to make it realistic
					animation.start((int) (1000/Math.pow(2, i)));
					animation.lockUntilCompletion();
				}
			}
			//Successful method execution - continue
			return true;
		} catch (InterruptedException ie) {
			ie.printStackTrace();
			//Failed method execution - stop
			return false;
		}
	}

	//Display the game start menu
	public void displayMenu() {
		//Get thread invoking this method
		Thread currentThread = Thread.currentThread();
		if (frame == null) {
			frame = new JFrame();
			frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
			currentWindowListener = new WindowListener() {
				public void windowDeactivated(WindowEvent e) {
				}
				public void windowActivated(WindowEvent e) {
				}
				public void windowDeiconified(WindowEvent e) {
				}
				public void windowIconified(WindowEvent e) {
				}
				public void windowClosed(WindowEvent e) {
				}
				public void windowClosing(WindowEvent e) {
					//Stop the thread which created this event listener, DO NOT use Thread.currentThread() here; will refer to the AWT event thread!
					currentThread.interrupt();
					//Free all resources
					stopEverything();
				}
				public void windowOpened(WindowEvent e) {
				}
			};
			frame.addWindowListener(currentWindowListener);
		} else {
			frame.getContentPane().removeAll();
		}
		frame.setTitle("Ping-pong game");

		//A JPanel to contain a scroll pane (with game options) along with the JPanel containing the "PLAY!"
		JPanel mainUIPanel = new JPanel();
		mainUIPanel.setLayout(new GridBagLayout());
		frame.getContentPane().add(mainUIPanel);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JScrollPane initScrollPane = new JScrollPane();
		initScrollPane.add(panel);
		initScrollPane.setViewportView(panel);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1.0f;
		c.weighty = 1.0f;
		c.fill = GridBagConstraints.BOTH;
		mainUIPanel.add(initScrollPane, c);
		frame.setSize(new Dimension(500, 500));
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 0;
		
		//Welcome the user!
		JLabel greetingLabel = new JLabel("Hey buddy! Welcome to this super-cool out-of-this-world ping-pong game! Let's get you set up!");
		panel.add(greetingLabel);

		//Tabs - play and options for good UX
		JTabbedPane tabbedGamePane = new JTabbedPane();
		panel.add(tabbedGamePane);

		//The panel contains extremely basic, quick, preset options (such as do AI) to play immediately. Default panel
		JPanel playPanel = new JPanel(new GridBagLayout());
		tabbedGamePane.addTab("Play", playPanel);

		//The panel contains more advanced options, typically for those who have a few extra seconds to spend
		JPanel optionsPanel = new JPanel(new GridBagLayout());
		tabbedGamePane.addTab("Options", optionsPanel);

		//The JPanel containing the list of players
		JPanel playersListPanel = new JPanel();
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 1;
		playersListPanel.setPreferredSize(new Dimension(360, 130));
		playPanel.add(playersListPanel, c);
		JLabel playersLabel = new JLabel("Below are the players and their names! What will your name be?");
		JScrollPane playersList = new JScrollPane();
		playersList.setPreferredSize(new Dimension(150, 100));
		JPanel playersListContent = new JPanel();
		playersListContent.setLayout(new BoxLayout(playersListContent, BoxLayout.Y_AXIS));
		playersList.add(playersListContent);
		playersList.setViewportView(playersListContent);

		//Declare before its initialisation to make it accessible to playWithAISelector's anonymous class scope
		JSlider AIIntelligenceLevel = new JSlider(JSlider.HORIZONTAL, 1, 3, 2);
		
		//Declare before newPlayer to put it in anonymous inner class scope for it is needed there
		JCheckBox playWithAISelector = new JCheckBox("Play against Larry the AI?");

		ActionListener AISelectorListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				AIIntelligenceLevel.setEnabled(playWithAISelector.isSelected());
			}
		};
		playWithAISelector.addActionListener(AISelectorListener);

		//Invoke action listener method (handler) to set necessary states
		AISelectorListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
		
		//Button to add a new player
		JButton newPlayer = new JButton("Add new player...");
		newPlayer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (playersListContent.getComponents().length - 1 >= maxPlayers) {
					JOptionPane.showMessageDialog(frame, "Sorry, the maximum amount of players, " + maxPlayers + ", has been reached.");
				} else {
					blockingTasksPool.submit(new Runnable() {
						/*This method is synchronous - it hangs the thread it runs on - DO NOT INVOKE IT ON THE AWT PAINTING THREAD FOR GOODNESS' SAKE!!!*/
						public void run() {
							try {
								TemporaryPlayer newPlayerTemplate = showCreatePlayerDialog("", new Color(0, 0, 0), null);
								createPlayerRepresentativeComponent(newPlayerTemplate, playersListContent, newPlayer, playWithAISelector.isSelected());
								if ((playersListContent.getComponents().length - 1 >= maxPlayers) || (playersListContent.getComponents().length >= maxPlayers && playWithAISelector.isSelected())) {
									newPlayer.setEnabled(false);
								}
							} catch (InterruptedException ie) {
								ie.printStackTrace();
							}
						}
					});
				}
			}
		});
		playersListContent.add(newPlayer);
		playersListPanel.add(playersLabel);
		playersListPanel.add(playersList);

		//Play with AI panel
		JPanel playWithAIPanel = new JPanel();
		playWithAIPanel.setLayout(new BoxLayout(playWithAIPanel, BoxLayout.PAGE_AXIS));
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 2;
		playPanel.add(playWithAIPanel, c);
		
		playWithAISelector.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				if (playWithAISelector.isSelected()) {
					if (playersListContent.getComponents().length - 1 >= maxPlayers) {
						JOptionPane.showMessageDialog(frame, "Sorry, the maximum amount of players, " + maxPlayers + ", has been reached.\nPlease remove a player if you would like to play against the AI");
						playWithAISelector.setSelected(false);
					} else if ((playersListContent.getComponents().length >= maxPlayers)) {
						newPlayer.setEnabled(false);
					}
				} else {
					if ((playersListContent.getComponents().length - 1 < maxPlayers)) {
						newPlayer.setEnabled(true);
					} else if ((playersListContent.getComponents().length - 1 >= maxPlayers)) {
						newPlayer.setEnabled(false);
					}
				}
			}
		});
		playWithAIPanel.add(playWithAISelector);
		
		//Put this and the playWithAIPanel in the playPanel
		JPanel AIIntelligencePanel = new JPanel();
		playWithAIPanel.add(AIIntelligencePanel);
		JLabel AIIntelligenceLabel = new JLabel("AI Intelligence level:");
		AIIntelligencePanel.add(AIIntelligenceLabel);
		JLabel selectedAIIntelligence = new JLabel();
		AIIntelligenceLevel.setMajorTickSpacing(1);
		AIIntelligenceLevel.setPaintTicks(true);
		AIIntelligenceLevel.setPaintLabels(true);
		ChangeListener AISliderListener = new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				switch (AIIntelligenceLevel.getValue()) {
					case 1:
						selectedAIIntelligence.setForeground(Color.GREEN);
						selectedAIIntelligence.setText("Patrick Star-level AI");
						gameAIIntelligenceLevel = AIModes.AI_STUPID;
						break;
					case 2:
						selectedAIIntelligence.setForeground(Color.BLUE);
						selectedAIIntelligence.setText("Your typical AI");
						gameAIIntelligenceLevel = AIModes.AI_TYPICAL;
						break;
					case 3:
						selectedAIIntelligence.setForeground(Color.RED);
						selectedAIIntelligence.setText("Dr. Nefario level vector-crunching AI");
						gameAIIntelligenceLevel = AIModes.AI_VECTOR;
						break;
					default:
						selectedAIIntelligence.setForeground(Color.BLACK);
						selectedAIIntelligence.setText("<Unknown>");
				}
			}
		};
		AIIntelligenceLevel.addChangeListener(AISliderListener);
		AIIntelligencePanel.add(AIIntelligenceLevel);
		playWithAIPanel.add(selectedAIIntelligence);
		//Set initial value of AI intelligence level label by programmatically invoking overriden method representing change of component state
		AISliderListener.stateChanged(new ChangeEvent(this));

		JPanel ballColorChooserPanel = new JPanel();
		ballColorChooserPanel.setLayout(new FlowLayout());
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 1;
		optionsPanel.add(ballColorChooserPanel, c);
		
		JLabel ballColorChooserLabel = new JLabel("Choose the desired ball colour!");
		ballColorChooserPanel.add(ballColorChooserLabel);
		JColorChooser ballColorChooserInput = new JColorChooser(new Color(255, 0, 0));
		ballColorChooserPanel.add(ballColorChooserInput);
		
		//JPanel containing UI to control ball speed
		JPanel ballSpeedChooserPanel = new JPanel();
		ballSpeedChooserPanel.setLayout(new FlowLayout());
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 2;
		optionsPanel.add(ballSpeedChooserPanel, c);
		//Contents of JPanel to control ball speed
		JLabel ballSpeedChooserLabel = new JLabel("Enter the desired ball speed!");
		ballSpeedChooserPanel.add(ballSpeedChooserLabel);
		JTextField ballSpeedChooserInput = new JTextField("10");
		ballSpeedChooserInput.setPreferredSize(new Dimension(50, 20));
		ballSpeedChooserPanel.add(ballSpeedChooserInput);
		JCheckBox randomDeflection = new JCheckBox("Ball deflects randomly?");
		randomDeflection.setSelected(true);
		ballSpeedChooserPanel.add(randomDeflection);
		
		//JPanel containing UI to choose player speed
		JPanel playerSpeedChooserPanel = new JPanel();
		playerSpeedChooserPanel.setLayout(new FlowLayout());
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 3;
		optionsPanel.add(playerSpeedChooserPanel, c);
		//Contents of JPanel to control player speed
		JLabel playerSpeedChooserLabel = new JLabel("Enter the desired player speed!");
		playerSpeedChooserPanel.add(playerSpeedChooserLabel);
		JTextField playerSpeedChooserInput = new JTextField("10");
		playerSpeedChooserInput.setPreferredSize(new Dimension(50, 20));
		playerSpeedChooserPanel.add(playerSpeedChooserInput);
		
		//JPanel to store competition modes
		JPanel playModePanel = new JPanel();
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 4;
		optionsPanel.add(playModePanel, c);
		//Prompt user to select competition mode
		JLabel playModeLabel = new JLabel("Select type of competiton during the game!");
		playModePanel.setLayout(new BoxLayout(playModePanel, BoxLayout.PAGE_AXIS));
		playModePanel.add(playModeLabel);
		//JPanel containing the possible choices as JRadioButtons and their mode-specific options
		JPanel competitionChoicesPanel = new JPanel();
		competitionChoicesPanel.setLayout(new BoxLayout(competitionChoicesPanel, BoxLayout.PAGE_AXIS));
		playModePanel.add(competitionChoicesPanel);
		ButtonGroup competitionTypesGroup = new ButtonGroup();

		//JPanel containing options for score target competiton mode
		JPanel targetSelectorPanel = new JPanel();
		//Appropriate prompt
		JLabel targetSelectorLabel = new JLabel("Select a score target to reach: ");
		JTextField targetSelectorField = new JTextField();
		targetSelectorField.setPreferredSize(new Dimension(50, 20));
		targetSelectorField.setText(Integer.toString(scoreTarget));
		targetSelectorPanel.add(targetSelectorLabel);
		targetSelectorPanel.add(targetSelectorField);

		JPanel timeLimitPanel = new JPanel();
		JLabel timeLimitLabel = new JLabel("Enter the game time limit");
		JTextField timeLimitField = new JTextField();
		timeLimitField.setPreferredSize(new Dimension(50, 20));
		timeLimitField.setText(Integer.toString(gameTimeLimit));
		timeLimitPanel.add(timeLimitLabel);
		timeLimitPanel.add(timeLimitField);


		JRadioButton noCompetitonOption = new JRadioButton("No competition - Free play");
		noCompetitonOption.setSelected(true);
		ActionListener noCompetitionRBListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				competitionMode = CompetitionMode.FREE_PLAY;
				targetSelectorField.setEnabled(false);
				timeLimitField.setEnabled(false);
			}
		};
		noCompetitonOption.addActionListener(noCompetitionRBListener);
		competitionTypesGroup.add(noCompetitonOption);
		competitionChoicesPanel.add(noCompetitonOption);
		noCompetitionRBListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));

		JRadioButton timeCompetitonOption = new JRadioButton("Time competition - Victor is he who scores the most points in a given time span");
		timeCompetitonOption.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				competitionMode = CompetitionMode.TIME_COMPETITION;
				targetSelectorField.setEnabled(false);
				timeLimitField.setEnabled(true);
			}
		});
		competitionTypesGroup.add(timeCompetitonOption);
		competitionChoicesPanel.add(timeCompetitonOption);

		competitionChoicesPanel.add(timeLimitPanel);

		JRadioButton targetCompetitonOption = new JRadioButton("Target competition - Victor is he who scores a given amount of points before his opponent");
		targetCompetitonOption.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				competitionMode = CompetitionMode.TARGET_COMPETITION;
				targetSelectorField.setEnabled(true);
				timeLimitField.setEnabled(false);
			}
		});
		competitionTypesGroup.add(targetCompetitonOption);
		competitionChoicesPanel.add(targetCompetitonOption);

		competitionChoicesPanel.add(targetSelectorPanel);
		
		competitionTypesGroup.setSelected(null, doBallTrailEffect);

		JPanel gameAudioPanel = new JPanel();
		gameAudioPanel.setLayout(new BoxLayout(gameAudioPanel, BoxLayout.PAGE_AXIS));
		gameAudioPanel.setBorder(BorderFactory.createDashedBorder(Color.BLUE, 5, 5));
		c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = 5;
		optionsPanel.add(gameAudioPanel, c);

		JPanel doBackgroundAudioPanel = new JPanel();
		gameAudioPanel.add(doBackgroundAudioPanel);

		JLabel doBackgroundAudioLabel = new JLabel("Enable background audio?");
		doBackgroundAudioPanel.add(doBackgroundAudioLabel);

		JSlider backgroundAudioVolumeSlider = new JSlider();
		JCheckBox doBackgroundAudio = new JCheckBox();
		//Used elsewhere - in its initial invocation
		ActionListener backgroundAudioCheckboxListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				backgroundAudioVolumeSlider.setEnabled(doBackgroundAudio.isSelected());
				//NEVER directly set doBgAudio (except for initialisation of the variable) - Always invoke this mutator method to do it; This method wakes the possibly sleeping audioPlayer thread to prepare it to play
				cinst.setBackgroundAudio(doBackgroundAudio.isSelected());
			}
		};
		doBackgroundAudio.setSelected(cinst.doBgAudio);
		doBackgroundAudio.addActionListener(backgroundAudioCheckboxListener);
		doBackgroundAudioPanel.add(doBackgroundAudio);

		//Set state accordingly by invoking its action listener
		//Alternative code to dispatchEvent(), which does not seem to work. Dispatches an event whilst respecting thread safety (Runnable executed on the Event Dispatch Thread) and in a queue style
		//TODO: IF POSSIBLE (maybe java.awt.Robot), substitute such rudimentary methods of event dispatching
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				backgroundAudioCheckboxListener.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
			}
		});

		JPanel backgroundAudioVolumePanel = new JPanel();
		gameAudioPanel.add(backgroundAudioVolumePanel);

		JLabel backgroundAudioVolumeLabel = new JLabel();
		doBackgroundAudioPanel.add(backgroundAudioVolumeLabel);

		backgroundAudioVolumeSlider.setMinimum(0);
		backgroundAudioVolumeSlider.setMaximum(100);
		backgroundAudioVolumeSlider.setValue(cinst.gameBgAudioVolume);
		ChangeListener backgroundAudioChangeListener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				backgroundAudioVolumeLabel.setText("Background audio volume: " + backgroundAudioVolumeSlider.getValue());
				cinst.gameBgAudioVolume = (byte) backgroundAudioVolumeSlider.getValue();
			}
		};
		backgroundAudioVolumeSlider.addChangeListener(backgroundAudioChangeListener);
		doBackgroundAudioPanel.add(backgroundAudioVolumeSlider);
		//Set state accordingly by invoking its action listener
		//Alternative code to dispatchEvent(), which does not seem to work. Dispatches an event whilst respecting thread safety (Runnable executed on the Event Dispatch Thread) and in a queue style
		//TODO: IF POSSIBLE (maybe java.awt.Robot), substitute such rudimentary methods of event dispatching
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				backgroundAudioChangeListener.stateChanged(new ChangeEvent(backgroundAudioVolumeSlider));
			}
		});


		JPanel doGameSoundPanel = new JPanel();
		gameAudioPanel.add(doGameSoundPanel);

		JLabel doGameSoundLabel = new JLabel("Enable game sound effects?");
		doGameSoundPanel.add(doGameSoundLabel);

		JSlider gameSoundVolumeSlider = new JSlider();
		JCheckBox doGameSound = new JCheckBox();
		//Used elsewhere - in its initial invocation
		ActionListener gameSoundCheckboxListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				gameSoundVolumeSlider.setEnabled(doGameSound.isSelected());
				cinst.doGameSounds = doGameSound.isSelected();
			}
		};
		doGameSound.setSelected(cinst.doGameSounds);
		doGameSound.addActionListener(gameSoundCheckboxListener);
		doGameSoundPanel.add(doGameSound);

		//Set state accordingly by invoking its action listener
		//Alternative code to dispatchEvent(), which does not seem to work. Dispatches an event whilst respecting thread safety (Runnable executed on the Event Dispatch Thread) and in a queue style
		//TODO: IF POSSIBLE (maybe java.awt.Robot), substitute such rudimentary methods of event dispatching
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				gameSoundCheckboxListener.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
			}
		});

		JPanel gameSoundVolumePanel = new JPanel();
		gameAudioPanel.add(gameSoundVolumePanel);

		JLabel gameSoundVolumeLabel = new JLabel();
		doGameSoundPanel.add(gameSoundVolumeLabel);

		gameSoundVolumeSlider.setMinimum(0);
		gameSoundVolumeSlider.setMaximum(100);
		gameSoundVolumeSlider.setValue(gameSoundsVolume);
		ChangeListener gameSoundChangeListener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				gameSoundVolumeLabel.setText("Game sound effect volume: " + gameSoundVolumeSlider.getValue());
				cinst.gameSoundsVolume = (byte) gameSoundVolumeSlider.getValue();
			}
		};
		gameSoundVolumeSlider.addChangeListener(gameSoundChangeListener);
		doGameSoundPanel.add(gameSoundVolumeSlider);
		//Set state accordingly by invoking its action listener
		//Alternative code to dispatchEvent(), which does not seem to work. Dispatches an event whilst respecting thread safety (Runnable executed on the Event Dispatch Thread) and in a queue style
		//TODO: IF POSSIBLE (maybe java.awt.Robot), substitute such rudimentary methods of event dispatching
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				gameSoundChangeListener.stateChanged(new ChangeEvent(gameSoundVolumeSlider));
			}
		});


		//A JPanel for the buttons (Restart and Advanced options) at the bottom
		JPanel buttonsWidgetPanel = new JPanel();
		c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 1;
		mainUIPanel.add(buttonsWidgetPanel, c);

		//Button to start playing
		JButton startGame = new JButton("Play!");
		//Code to be executed on button click - starting the game
		startGame.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int ballSpeed, playerSpeed;
				//Validation - Check appropriate number of players (AI included) and legal ball speed and player speed
				if ((playWithAISelector.isSelected() && playersListContent.getComponents().length - 1 < 1) || (!playWithAISelector.isSelected() && playersListContent.getComponents().length - 1 <= 1)) {
					JOptionPane.showMessageDialog(frame, "Sorry, at least two players (AI included) are required to play!");
					return;
				}
				try {
					ballSpeed = Integer.parseInt(ballSpeedChooserInput.getText());
					if (ballSpeed < 1 || ballSpeed > 1000) {
						JOptionPane.showMessageDialog(frame, "The field specifying the ball speed must be a positive integer between 1 and 1,000!");
						return;
					}
				} catch (NumberFormatException nfe) {
					if (ballSpeedChooserInput.getText().length() == 0) {
						JOptionPane.showMessageDialog(frame, "The field specifying the ball speed is mandatory - it MUST be filled in!");
					} else {
						JOptionPane.showMessageDialog(frame, "The field specifying the ball speed must be a positive integer between 1 and 1,000!");
					}
					return;
				}
				if (ballXComponentLowerBound >= ballSpeed) {
					JOptionPane.showMessageDialog(frame, "The lowest accepted x-component of the ball must not exceed its speed (Check advanced settings)");
					return;
				}
				try {
					playerSpeed = Integer.parseInt(playerSpeedChooserInput.getText());
					if (playerSpeed < 1 || playerSpeed > 1000) {
						JOptionPane.showMessageDialog(frame, "The field specifying the player speed must be a positive integer between 1 and 1,000!");
						return;
					}
				} catch (NumberFormatException nfe) {
					if (playerSpeedChooserInput.getText().length() == 0) {
						JOptionPane.showMessageDialog(frame, "The field specifying the player speed is mandatory - it MUST be filled in!");
					} else {
						JOptionPane.showMessageDialog(frame, "The field specifying the player speed must only contain a positive integer between 1 and 1,000!");
					}
					return;
				}
				//Set state based on competition mode (e.g.: Timer for time competition mode)

				if (competitionMode == PingPongGame.CompetitionMode.TIME_COMPETITION) {
					//Time competition
					try {
						//Parse the time limit field (with validation)
						gameTimeLimit = Integer.parseInt(timeLimitField.getText());
						if (gameTimeLimit < 1) {
							JOptionPane.showMessageDialog(frame, "The field specifying the desired game time limit (time competitive mode only) must be a positive integer NOT SMALLER THAN 1");
							return;
						}
					} catch (NumberFormatException nfe) {
						JOptionPane.showMessageDialog(frame, "The field specifying the desired game time limit (time competitive mode only) must be a positive INTEGER not smaller than 1 (No non-numerical characters or decimal points accepted)");
						return;
					}
				} else if (competitionMode == PingPongGame.CompetitionMode.TARGET_COMPETITION) {
					//Target competition
					try {
						//Parse the score target field (with validation)
						scoreTarget = Integer.parseInt(targetSelectorField.getText());
						if (scoreTarget < 1) {
							JOptionPane.showMessageDialog(frame, "The field specifying the desired score target (target competitive mode only) must be a positive integer NOT SMALLER THAN 1");
							return;
						}
					} catch (NumberFormatException nfe) {
						JOptionPane.showMessageDialog(frame, "The field specifying the desired score target (target competitive mode only) must be a positive INTEGER not smaller than 1 (No non-numerical characters or decimal points accepted)");
						return;
					}
				}
				//setGameEnvironment invokes a number of blocking methods; send its invocation to the thread pool which handles blocking events
				blockingTasksPool.submit(new Runnable() {
					@Override
					public void run() {
						setGameEnvironment(ballSpeed, playerSpeed, playWithAISelector.isSelected(), randomDeflection.isSelected(), ballColorChooserInput.getColor());
					}
				});
			}
		});
		buttonsWidgetPanel.add(startGame);
		//c.gridx = 1;
		//panel.add(startGame, c);

		//Button to open advanced settings menu
		JButton advancedSettings = new JButton("Open Advanced Settings...");
		advancedSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Submit the blocking method which opens the advanced settings menu and awaits the user's response
				blockingTasksPool.submit(new Runnable() {
					@Override
					public void run() {
						displayAdvancedSettings();
					}
				});
			}
		});
		/*c.gridx = 2;
		c.gridy = 7;
		panel.add(advancedSettings, c);*/
		buttonsWidgetPanel.add(advancedSettings);	

		frame.pack();
		frame.setVisible(true);

		//Load default player
		HashMap<KeyStroke, Integer> defaultTemplateKeyBindings = new HashMap<KeyStroke, Integer>();
		defaultTemplateKeyBindings.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), Player.UP);
		defaultTemplateKeyBindings.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), Player.DOWN);
		TemporaryPlayer defaultTemplate = new TemporaryPlayer("Player 1", defaultTemplateKeyBindings, new Color(0, 0, 0), this);
		createPlayerRepresentativeComponent(defaultTemplate, playersListContent, newPlayer, playWithAISelector.isSelected());
		if ((playersListContent.getComponents().length - 1 >= maxPlayers) || (playersListContent.getComponents().length >= maxPlayers && playWithAISelector.isSelected())) {
			newPlayer.setEnabled(false);
		}

	}
	//Constructor to display splash screen and menu
	public PingPongGame() {
		//Display animated splash screen to ease and intrigue user
		if (displaySplashScreen((long) (2000 + (Math.random() * 5000)))) {
			//Display the start game menu
			displayMenu();
		} else {
			//Thread interrupted whilst displaying splash screen - stop game immediately
			stopEverything();
		}
	}
	//Code adapted from https://stackoverflow.com/questions/4578835/how-do-i-draw-various-shapes-in-java-which-library-should-i-use
	//All usage of graphics context objects adapted from https://docs.oracle.com/javase/8/docs/api/java/awt/Graphics.html and https://docs.oracle.com/javase/8/docs/api/java/awt/Graphics2D.html
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		FontMetrics fm;
		Graphics2D g2d = (Graphics2D) g;
		g2d.setFont(defaultFont);
		for (PhysicalObject o : PhysicalObject.getRegisteredObjects()) {
			g2d.setColor(o.color);
			if (o instanceof Oval) {
				Oval oval = (Oval) o;
				//Draw ball trail effect if applicable
				if (o == gameBall && doBallTrailEffect && ballTrailCoords != null) {
					for (int i = 0; i < ballTrailCoords.length; i += 3) {
						//Get type of particle from data int ((3 * n + 2)th index of array)
						if (ballTrailCoords[i + 2] >> 24 == 0/*2^(8*3) = 16777216 -> mathematically getting the contents of the very first byte of an int*/) {
							//Typical ball trail with decreasing opacity with distance
							g2d.setColor(new Color(o.color.getRed(), o.color.getGreen(), o.color.getBlue(), (int) (255.0f /*Maximum is 1.0f, however it is casted to int, so max -> 255*/ * i/(ballTrailCoords.length * 2))));
							if (doTrailParticleShrinking) {
								g2d.fillOval((int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], (int) (o.width * ((double) i)/ballTrailCoords.length), (int) (o.height * ((double) i)/ballTrailCoords.length));
							} else {
								g2d.fillOval((int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], (int) o.width, (int) o.height);
							}
						} else if (ballTrailCoords[i + 2] >> 24 == 1) {
							//Star trail
							switch ((ballTrailCoords[i + 2] >> 16 /*2^(2*8) = 65536*/) % 128 /*2^8 = 128 -> Getting the contents of the second byte of an int*/) {
								case YELLOW_STAR:
									//Draw a yellow star
									if (doTrailParticleShrinking) {
										g2d.drawImage(yellowStar, (int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], (int) (o.width * ((double) i)/ballTrailCoords.length), (int) (o.height * ((double) i)/ballTrailCoords.length), this);
									} else {
										g2d.drawImage(yellowStar, (int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], this);
									}
									break;
								case GREEN_STAR:
									//Draw a green star
									if (doTrailParticleShrinking) {
										g2d.drawImage(greenStar, (int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], (int) (o.width * ((double) i)/ballTrailCoords.length), (int) (o.height * ((double) i)/ballTrailCoords.length), this);
									} else {
										g2d.drawImage(greenStar, (int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], this);
									}
									break;
								case BLUE_STAR:
									//Draw a blue star
									if (doTrailParticleShrinking) {
										g2d.drawImage(blueStar, (int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], (int) (o.width * ((double) i)/ballTrailCoords.length), (int) (o.height * ((double) i)/ballTrailCoords.length), this);
									} else {
										g2d.drawImage(blueStar, (int) ballTrailCoords[i], (int) ballTrailCoords[i + 1], this);
									}
									break;
							}
						}
					}
				}
				g2d.setColor(o.color);
				g2d.fillOval((int) (oval.x), (int) (oval.y), (int) (oval.width), (int) (oval.height));
			} else if (o instanceof Rectangle) {
				Rectangle rect = (Rectangle) o;
				g2d.fillRect((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height);
			}
		}
		//Code adapted from https://www.geeksforgeeks.org/synchronization-in-java/
		//Draw all counters by passing graphics context and JComponent to draw on
		synchronized(Counter.getRegisteredCounters()) {
			for (Counter counter : Counter.getRegisteredCounters()) {
				counter.paint(g2d, this);
			}
		}
		//Draw all components (e.g.: text, button) by passing graphics context
		synchronized(AbstractComponent.getRegisteredComponents()) {
			for (AbstractComponent component : AbstractComponent.getRegisteredComponents()) {
				component.paint(g2d);
			}
		}
		//Draw all animations by passing graphics context and JComponent to draw on
		synchronized(AbstractAnimation.getActiveAnimations()) {
			for (AbstractAnimation animation : AbstractAnimation.getActiveAnimations()) {
				animation.paint(g2d, this);
			}
		}
		g2d.setColor(Color.BLACK);
		//Try catch references: help from the teacher + https://www.geeksforgeeks.org/exceptions-in-java/
		try {
			g2d.setFont(defaultFont);
			//Draw players' scoreBoards
			for (Player player : Player.getRegisteredPlayers()) {
				if (player.scoreVisible) {
					if (player.scoreOppSide) {
						//Get the graphics FontMetrics, to measure the dimensions of text to draw scoreboards from the right side with a given offset
						fm = g2d.getFontMetrics();
						g2d.drawString(player.name + ": " + player.score, player.scoreX - fm.stringWidth(player.name + ": " + player.score), player.scoreY);
					} else {
						g2d.drawString(player.name + ": " + player.score, player.scoreX, player.scoreY);
					}
				}
			}
		} catch (java.util.ConcurrentModificationException cme) {
			cme.printStackTrace();
		}
		if (cinst.paused) {
			//Draw paused indicator in the middle of the screen with the default font
			g2d.setFont(defaultFont);
			fm = g2d.getFontMetrics();
			g2d.drawString(pausedMessage, (cinst.getWidth() - fm.stringWidth(pausedMessage))/2, (cinst.getHeight() - fm.getAscent())/2);
		}
	}
	public boolean resetScene(String message, Color color) throws InterruptedException {
		if (this.getNumPauseReasons() == 0) {
			this.latestApplicableGameSeizeTimestamp = System.currentTimeMillis();
		}
		this.resettingScene = true;
		pauseBtn.setEnabled(false);
		//Resetting scene - destroy butterfly
		if (this.doingButterfly) {
			//Stop the animation
			this.doingButterfly = false;
			//Hide the butterfly image
			//No need to use synchronized(cinst.butterflyImage) {} here - this code is executed on AWT event dispatch thread
			this.butterflyImage.setVisible(false);
			//If doing butterfly, set time for next butterfly!
			cinst.nextButterflyTime = (long) (System.currentTimeMillis() + Math.random() * 120000);
			//DO NOT destroy/nullify the reference to the butterfly image
		}
		if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION && gameTimer.getActiveStatus() && !gameTimer.isPaused()) {
			gameTimer.pause();
		}
		for (Player p : Player.getRegisteredPlayers()) {
			p.setY((cinst.getHeight()-p.getHeight())/2);
		}
		gameBall.x = (cinst.getWidth()-gameBall.width)/2;
		gameBall.y = (cinst.getHeight()-gameBall.height)/2;
		if (doBallTrailEffect) {
			for (int i = 0; i < ballTrailCoords.length; i += 3) {
				if (doRandomTrailPositions) {
					ballTrailCoords[i] = (int) (Math.random() * gameBall.motion[0]);
					ballTrailCoords[i + 1] = (int) (Math.random() * gameBall.motion[1]);
					int cachedNum = ballTrailCoords[i];
					double randomRadian = Math.random() * 2 * Math.PI;
					ballTrailCoords[i] = (int) (gameBall.x + cachedNum * Math.cos(randomRadian) - ballTrailCoords[i + 1] * Math.sin(randomRadian));
					ballTrailCoords[i + 1] = (int) (gameBall.y + cachedNum * Math.sin(randomRadian) + ballTrailCoords[i + 1] * Math.cos(randomRadian));
				} else {
					ballTrailCoords[i] = (int) gameBall.x;
					ballTrailCoords[i + 1] = (int) gameBall.y;
				}
				if (ballTrailType == BALL_TRAIL) {
					ballTrailCoords[i + 2] = 0; // Any extra trail data if applicable - 0 means no data - ball by default
				} else if (ballTrailType == STAR_TRAIL) {
					ballTrailCoords[i + 2] = /*Fade type: Star*/ (1 * 16777216) + /*Star colour - randomise it*/ ((int) (Math.random() * 3)) * 65536;
				}
			}
		}
		double randomXComponent = (ballSpeed - ballXComponentLowerBound) * Math.random();
		Player.pauseAllPlayers();
		CountdownAnimation countdown = new CountdownAnimation(3000);
		countdown.messageStr = message;
		countdown.messageColor = color;
		try {
			countdown.start(true);
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
		//The countdown may have been forcefully stopped
		if (countdown.getTimerState() == TimerClass.TimerState.STOPPED) {
			return false;
		}
		Player.resumeAllPlayers();
		this.gameBall.motion[0] = (ballXComponentLowerBound + randomXComponent) * Math.signum(Math.random() - 0.5);
		this.gameBall.motion[1] = (ballSpeed - randomXComponent - ballXComponentLowerBound) * Math.signum(Math.random() - 0.5);
		System.out.println("x-motion: " + gameBall.motion[0] + ", y-motion: " + gameBall.motion[1]);
		gameBall.updateCachedMotionData(gameBall);
		//Resume the game timer if applicable
		if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION && gameTimer.getActiveStatus()) {
			gameTimer.resume();
		}
		//Resume the game background audio player if applicable
		if (cinst.audioPlayer.isActive() && cinst.doBgAudio && cinst.audioPlayer.isPaused()) {
			cinst.audioPlayer.resume();
		}
		if (this.getNumPauseReasons() == 1) {
			this.nextButterflyTime += (System.currentTimeMillis() - this.latestApplicableGameSeizeTimestamp);
		}
		this.resettingScene = false;
		pauseBtn.setEnabled(true);
		return true;
	}
	public void declareVictor() throws InterruptedException {
		//Any possible final action is done now, seeing as victor will (shortly) be declared!
		cinst.finalGameAction = false;
		//Lock all players and objects - GAME OVER!
		this.lockAllObjects = true;
		//Check if the game is a time-competition game
		if (competitionMode == CompetitionMode.TIME_COMPETITION && gameTimer.getActiveStatus()) {
			//Usually bypassed on time competition, seeing as this method is generally invoked AFTER the timer has stopped
			gameTimer.stop();
		}
		TextComponent component = new TextComponent(0, 0, 0.0f, cinst, 0.5f, 0.5f, new Color(255, 0, 0), defaultFont.deriveFont(50.0f), "GAME OVER!");
		long now = System.currentTimeMillis();
		while (System.currentTimeMillis() <= now + 1000) {
			synchronized(component) {
				component.color = new Color((float) Math.abs(Math.sin(System.currentTimeMillis() - now * (Math.PI/180))), (float) Math.abs(Math.sin(System.currentTimeMillis() - now * (Math.PI/180) + Math.PI/3)), (float) Math.abs(Math.sin(System.currentTimeMillis() - now * (Math.PI/180) + 2/3 * Math.PI)));
				component.rotation = (36 * (System.currentTimeMillis() - now)/100) % 360;
			}
			Thread.sleep(10);
		}
		component.rotation = 0;
		component.text = "The winner is...";
		component.color = Color.BLUE;
		now = System.currentTimeMillis();
		while (System.currentTimeMillis() <= now + 2000) {
			synchronized(component) {
				component.font = new Font(component.font.getName(), Font.PLAIN, (int) (3 * (System.currentTimeMillis() - now)/100.0f));
			}
			Thread.sleep(10);
		}
		//component.remove();

		if (Player.getRegisteredPlayers().get(0).score > Player.getRegisteredPlayers().get(1).score) {
			component.color = Player.getRegisteredPlayers().get(0).getPlayerObject().color;
			component.setText(Player.getRegisteredPlayers().get(0).name);
		} else if (Player.getRegisteredPlayers().get(0).score < Player.getRegisteredPlayers().get(1).score) {
			component.color = Player.getRegisteredPlayers().get(1).getPlayerObject().color;
			component.setText(Player.getRegisteredPlayers().get(1).name);
		} else if (Player.getRegisteredPlayers().get(0).score == Player.getRegisteredPlayers().get(1).score) {
			component.color = Color.GREEN;
			component.setText("Everybody! It's a draw!!!");
		} else {
			component.color = Color.BLACK;
			component.setText("I don't know... and I probably never will...\nknow that is...");
		}

		//Declare winner and put two options (restart and possible last goal (action)) beneath the text

		cinst.restartButton = new Button(-100, 0, 0.5f, 0.65f, CoordinateRatiosable.DYNAMIC_COMPUTE, CoordinateRatiosable.DYNAMIC_COMPUTE, 0.0f, cinst, "Restart game?", Color.BLUE, Color.YELLOW, defaultFont.deriveFont(50.0f));
		cinst.restartButton.setCurvedEdgesRadius(15);
		cinst.restartButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				cinst.restartButton.remove();
				if (cinst.lastGoalButton != null) {
					cinst.lastGoalButton.remove();
				}
				component.remove();
				//All initial game internal state set through the following method
				startPlaying(ballSpeed, playingWithAI, gameBall.color, playerSpeed, randomBallDeflection);
			}
		});
		if (Player.getRegisteredPlayers().get(0).score == Player.getRegisteredPlayers().get(1).score) {
			//restart.setXScreenRatio(0.3f);
			cinst.lastGoalButton = new Button(-100, 50, 0.5f, 0.65f, CoordinateRatiosable.DYNAMIC_COMPUTE, CoordinateRatiosable.DYNAMIC_COMPUTE, 0.0f, cinst, "Last goal wins?", Color.RED, Color.BLUE, defaultFont.deriveFont(50.0f));
			cinst.lastGoalButton.setCurvedEdgesRadius(15);
			cinst.lastGoalButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					cinst.restartButton.remove();
					if (cinst.lastGoalButton != null) {
						cinst.lastGoalButton.remove();
					}
					component.remove();
					//Set last action state after destroying all components to avoid some illegal (invalid) states due to concurrency
					cinst.hasDeclaredVictor = false;
					cinst.finalGameAction = true;
					cinst.lockAllObjects = false;
				}
			});
		}
	}

	public void setBackgroundAudio(boolean doBgAudio) {
		//Always set the variable first; the thread may potentially not see a change in state and fall asleep again, probably causing the player to not play
		this.doBgAudio = doBgAudio;
		if (doBgAudio && this.audioThread != null /*Handling the possible case of this method's invocation before the initialisation of the audioThread*/) {
			//The audio player may be asleep, waiting to play audio. Notify it!
			synchronized(this.audioThread) {
				this.audioThread.notifyAll();
			}
		}
	}


	public int getNumPauseReasons() {
		int num = 0;
		if (this.paused) {
			num++;
		}
		if (this.restarting) {
			num++;
		}
		if (this.resettingScene) {
			num++;
		}
		return num;
	}

	//Should only be invoked ONCE
	public void setGameEnvironment(int ballSpeed, int playerSpeed, boolean doAI, boolean randomBallDeflection, Color ballColor) {
		//Load the necessary resources (e.g.: textures and SHORT game sound effects) into memory
		try {
			yellowStar = ImageIO.read(getClass().getResource("media/yellow_star.png"));
			greenStar = ImageIO.read(getClass().getResource("media/green_star.png"));
			blueStar = ImageIO.read(getClass().getResource("media/blue_star.png"));
			scoreSoundPlayer = new AudioPlayer();
			scoreSoundPlayer.loadAudioFile(getClass().getResourceAsStream("media/score.mp3"));
			impactSoundPlayer = new AudioPlayer();
			impactSoundPlayer.loadAudioFile(getClass().getResourceAsStream("media/impact.mp3"));
			butterflyImage = new Image(0.0, 0.0, 100.0, 100.0, 35.0f, cinst, ImageIO.read(getClass().getResourceAsStream("media/butterfly.png")));
			//Hide the butterfly image
			synchronized(butterflyImage) {
				butterflyImage.setVisible(false);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (UnsupportedAudioFileException uafe) {
			uafe.printStackTrace();
		} catch (LineUnavailableException lue) {
			lue.printStackTrace();
		}
		//Set the game ball object
		this.gameBall = new Oval(this.getWidth()/2 - ballRadius, this.getHeight()/2 - ballRadius, 2*ballRadius, 2*ballRadius, ballColor);
		//Clear the initialisation UI
		frame.getContentPane().removeAll();
		//When the window's full screen mode is toggled, move the players' sliders accordingly
		frame.addWindowStateListener(new WindowStateListener() {
			public void windowStateChanged(WindowEvent e) {
				for (Player p : Player.getRegisteredPlayers()) {
					if (p.side == TemporaryPlayer.RIGHT) {
						p.setX(cinst.getWidth() - 40);
						p.scoreX = cinst.getWidth() - 20;
					}
				}
			}
		});
		//When the window is resized or full screen mode is toggled, move the players' sliders accordingly
		frame.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				for (Player p : Player.getRegisteredPlayers()) {
					if (p.side == TemporaryPlayer.RIGHT) {
						p.setX(cinst.getWidth() - 40);
						p.scoreX = cinst.getWidth() - 20;
					}
				}
			}
		});
		//The mainPanel contains the game panel (this PingPongGame instance) and the top meubar with buttons to control the game (e.g.: pause, restart)
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		JPanel optionsPanel = new JPanel();
		//Pause button to pause the game whilst playing
		pauseBtn = new JButton("Pause...");
		//The button is not greyed out
		pauseBtn.setEnabled(false);
		//Make the pause button pause/resume the game depending on the current game state
		pauseBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cinst.requestFocus();
				if (cinst.paused) {
					//TODO: Synchronize cinst object whilst changing the state of the program during pause, resume, attempt restart and abort restart operations BEFORE the blocking method
					//Do this before setting cinst.paused to false to prevent the physics thread from getting a bad state (negative time remaining yet game paused or attempting restart)
					if (cinst.getNumPauseReasons() == 1) {
						cinst.nextButterflyTime += (System.currentTimeMillis() - cinst.latestApplicableGameSeizeTimestamp);
					}
					pauseBtn.setText("Pause...");
					if (cinst.getNumPauseReasons() == 1 && cinst.paused) {
						//Now running resume countdown animation
						cinst.resuming = true;
						//Set it here in order to read 1 for the butterfly timestamp setter yet 0 for the resume operation
						cinst.paused = false;
						//Play the restart countdown animation and wait for its completion (a blocking task) to the blockingTasksPool
						blockingTasksPool.submit(new Runnable() {
							public void run() {
								/*animateCountdown(3, "Resuming...", Color.BLACK);*/
								CountdownAnimation countdown = new CountdownAnimation(3000);
								countdown.messageStr = "Resuming...";
								try {
									//Currently resuming - play appropriate animation
									countdown.start(true);
								} catch (InterruptedException ie) {
									ie.printStackTrace();
								} finally {
									//This code must ALWAYS be executed, even in the case of interruption (shutdown of ExecutorService)
									//AFTER getting a lock on cinst (the current PingPongGame instance), set restarting to false - restart animation finished
									synchronized(cinst) {
										cinst.resuming = false;
									}
								}

								//Do not resume the game if the animation was forcefully (prematurely) stopped, for instance by a restart
								if (countdown.getTimerState() != TimerClass.TimerState.STOPPED) {
									if (cinst.getNumPauseReasons() == 0) {
										Player.resumeAllPlayers();
										if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION) {
											cinst.gameTimer.resume();
										}
										//Resume the audio player if applicable
										if (cinst.audioPlayer.isActive() && cinst.doBgAudio && cinst.audioPlayer.isPaused()) {
											cinst.audioPlayer.resume();
										}
									}
								}
							}
						});
					}
				} else {
					if (cinst.getNumPauseReasons() == 0) {
						cinst.latestApplicableGameSeizeTimestamp = System.currentTimeMillis();
					}
					cinst.paused = true;
					pauseBtn.setText("Resume...");
					Player.pauseAllPlayers();
					if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION) {
						gameTimer.pause();
					}
					if (cinst.audioPlayer.isActive() && cinst.doBgAudio) {
						synchronized(cinst.audioPlayer) {
							cinst.audioPlayer.pause();
						}
					}
				}
			}
		});

		JButton restartBtn = new JButton("Restart game...");
		restartBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (cinst.getNumPauseReasons() == 0) {
					//If the game was not paused when this button was clicked, set the latest pause (game seize) time to the current time (now)
					cinst.latestApplicableGameSeizeTimestamp = System.currentTimeMillis();
				}
				cinst.restarting = true;
				boolean wasGameTimerPaused = gameTimer.isPaused(), wasBgMusicPaused = audioPlayer.isPaused();
				cinst.requestFocus();
				Player.pauseAllPlayers();
				AbstractAnimation.pauseAllAnimations();
				if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION && gameTimer.getActiveStatus() && !wasGameTimerPaused) {
					gameTimer.pause();
				}
				if (cinst.doBgAudio && cinst.audioPlayer.isActive() && !wasBgMusicPaused) {
					synchronized(cinst.audioPlayer) {
						cinst.audioPlayer.pause();
					}
				}
				if (JOptionPane.showConfirmDialog(frame, "This will restart the game; everybody's points will be reset to 0. Are you sure you want to continue?", "Restart confirmation", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					//Code to restart game
					//Do it here before blocking method
					cinst.restarting = false;
					//Do not set pausing state variables to zero here - it will be done in the startPlaying method invocation
					if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION && gameTimer.getActiveStatus()) {
						gameTimer.stop();
					}
					if (cinst.doBgAudio && cinst.audioPlayer.isActive()) {
						//Signify pausing before restart
						cinst.audioThread.setAudioThreadPaused(true);
						cinst.audioThread.signifyRestart();
					}
					//Stop all animations, particularly the starting animation
					AbstractAnimation.stopAllAnimations();
					//No need to set restarting to false - stopping the animations will execute the 'finally' block of the submitted service, which sets restart to false.
					//Restart the game
					//Set the next butterfly time
					if (gameAIIntelligenceLevel == AIModes.AI_VECTOR) {
						cinst.nextButterflyTime = (long) (System.currentTimeMillis() + Math.random() * 120000);
					}
					//DO NOT invoke this blocking method on the AWT event thread! This will lock up the UI and event dispatching for the duration of the method's execution! Invoke it elsewhere (on the blockingTasksPool)!
					blockingTasksPool.submit(new Runnable() {
						@Override
						public void run() {
							startPlaying(cinst.ballSpeed, cinst.playingWithAI, cinst.gameBall.color, cinst.playerSpeed, cinst.randomBallDeflection);
							cinst.audioThread.setAudioThreadPaused(false);
						}
					});
				} else {
					//Abort restart
					//Synchronize cinst object whilst changing the state of the program during pause, resume, attempt restart and abort restart operations BEFORE the blocking method
					synchronized(cinst) {
						if (cinst.getNumPauseReasons() == 1) {
							cinst.nextButterflyTime += (System.currentTimeMillis() - cinst.latestApplicableGameSeizeTimestamp);
						}

						//Set the restarting flag to false before checking the number of reasons to pause the game!
						cinst.restarting = false;

						//The game might be restarting - if so, another runnable/event handler will (sooner or later) resume the game
						if (cinst.getNumPauseReasons() == 0 && !cinst.resuming) {
							Player.resumeAllPlayers();
							AbstractAnimation.resumeAllAnimations();
							if (cinst.competitionMode == CompetitionMode.TIME_COMPETITION && gameTimer.getActiveStatus() && !wasGameTimerPaused) {
								gameTimer.resume();
							}
							if (cinst.doBgAudio && cinst.audioPlayer.isActive() && !wasBgMusicPaused) {
								cinst.audioPlayer.resume();
							}
						} else if ((cinst.getNumPauseReasons() == 1 && cinst.resettingScene) || cinst.resuming) {
							//Might be resetting scene (e.g.: at time of restart/goal) or in the process of a restart animation
							AbstractAnimation.resumeAllAnimations();
							if (cinst.doBgAudio && cinst.audioPlayer.isActive() && !wasBgMusicPaused) {
								cinst.audioPlayer.resume();
							}
						}
					}
				}
			}
		});

		frame.getContentPane().add(mainPanel);
		mainPanel.add(optionsPanel, BorderLayout.NORTH);
		mainPanel.add(cinst, BorderLayout.CENTER);
		optionsPanel.add(pauseBtn);
		optionsPanel.add(restartBtn);
		//Must repaint/revalidate the WHOLE frame (only once, of course). This is to remove any particularly stubborn upper part of the previous UI
		frame.getContentPane().revalidate();
		frame.getContentPane().repaint();

		this.graphicsThread = new Thread() {
			@Override
			public void run() {
				try {
					while (true) {
						/*for (PhysicalObject o : PhysicalObject.getRegisteredObjects()) {
							cinst.repaint((int) o.x, (int) o.y, (int) o.width, (int) o.height);
						}
						for (Player p : Player.getRegisteredPlayers()) {
							if (p.scoreOppSide) {
								sWidth = fm.stringWidth(p.name + ": " + p.score);
								cinst.repaint((int) (p.scoreX - sWidth), (int) p.scoreY, sWidth, fm.getAscent());
							} else {
								cinst.repaint((int) p.scoreX - fm.stringWidth(p.name + ": " + p.score), (int) p.scoreY, fm.stringWidth(p.name + ": " + p.score), fm.getAscent());
							}
						}*/
						cinst.repaint();
						Thread.sleep((long) (1.0/fps * 1000));
					}
				} catch (InterruptedException ie) {
					ie.printStackTrace();
				}
				System.out.println("Graphics thread finished");
			}
		};
		this.graphicsThread.start();
		this.physicsThread = new Thread() {
			double[] tempArr;
			double randomXComponent;
			@Override
			public void run() {
				try {
					while (true) {
						if (!cinst.lockAllObjects) {
							if (competitionMode == CompetitionMode.TIME_COMPETITION) {
								if (gameTimer.getRemainingTime() <= 0 && !cinst.hasDeclaredVictor && !cinst.finalGameAction) {
									//Stop the game and the timer and get a victor
									cinst.hasDeclaredVictor = true;
									declareVictor();
								} else {
									//Update the counter
									gameObjectiveCounter.value = TimeUtils.toFormattedTime(gameTimer.getRemainingTime()/1000);
								}
							}
							if (!Player.areAllPlayersPaused()) {
								for (PhysicalObject o : PhysicalObject.getRegisteredObjects()) {
									o.x += o.motion[0];
									o.y += o.motion[1];
									tempArr = o.motion;
									if (!(tempArr[0] == 0 && tempArr[1] == 0)) {
										o.motion[0] += (o.acceleration/fps) * Math.atan(tempArr[1]/tempArr[0]) * (180/Math.PI)/90;
										if (o.ignoreGravity) {
											o.motion[1] += (o.acceleration/fps) * (90 - Math.atan(tempArr[1]/tempArr[0]) * (180/Math.PI))/90;
										} else {
											o.motion[1] += (o.acceleration/fps) * (90 - Math.atan(tempArr[1]/tempArr[0]) * (180/Math.PI))/90 + gravity/fps;
										}
									}
									if (o == gameBall) {
										if (o.x <= 0) {
											o.x = 0;
											Player.getRegisteredPlayers().get(1).score++;
											if (cinst.doGameSounds) {
												// Play goal sound
												gameSounds.playGameSound(GameSoundEffectManager.GOAL);
											}
											if (competitionMode == CompetitionMode.TARGET_COMPETITION || cinst.finalGameAction) {
												if ((Player.getRegisteredPlayers().get(1).score == scoreTarget || cinst.finalGameAction) && !cinst.hasDeclaredVictor) {
													cinst.hasDeclaredVictor = true;
													declareVictor();
												} else {
													resetScene("", Color.BLACK);
												}
											} else {
												resetScene("", Color.BLACK);
											}
										} else if (o.x + o.width >= getWidth()) {
											o.x = cinst.getWidth() - o.width;
											Player.getRegisteredPlayers().get(0).score++;
											if (cinst.doGameSounds) {
												// Play goal sound
												gameSounds.playGameSound(GameSoundEffectManager.GOAL);
											}
											if (cinst.competitionMode == CompetitionMode.TARGET_COMPETITION || cinst.finalGameAction) {
												if ((Player.getRegisteredPlayers().get(0).score == scoreTarget || cinst.finalGameAction) && !cinst.hasDeclaredVictor) {
													cinst.hasDeclaredVictor = true;
													declareVictor();
												} else {
													resetScene("", Color.BLACK);
												}
											} else {
												resetScene("", Color.BLACK);
											}
										} else if (o.y <= 0) {
											o.y = 0;
											o.motion[1] = Math.abs(o.motion[1]) * (1-o.velocityChangeOnCollision);
										} else if (o.y + o.height >= getHeight()) {
											o.y = getHeight() - o.height;
											o.motion[1] = -Math.abs(o.motion[1]) * (1-o.velocityChangeOnCollision);
										}
										for (Player p : Player.getRegisteredPlayers()) {
											switch (o.getCollisionData(p.getPlayerObject())) {
												case PhysicalObject.TOP:
													if (cinst.randomBallDeflection) {
														randomXComponent = (ballSpeed - ballXComponentLowerBound) * Math.random();
														o.motion[0] = (ballXComponentLowerBound + randomXComponent) * Math.signum(-o.motion[0]);
														o.motion[1] = (ballSpeed - randomXComponent - ballXComponentLowerBound) * Math.signum(-o.motion[1]);
													} else {
														o.motion[1] = -o.motion[1];
													}
													o.y = p.getPlayerObject().y - o.height;
													gameBall.updateCachedMotionData(gameBall);
													if (cinst.doGameSounds) {
														//Play deflection sound
														gameSounds.playGameSound(GameSoundEffectManager.WALL_IMPACT);
													}
													break;
												case PhysicalObject.RIGHT:
													if (cinst.randomBallDeflection) {
														randomXComponent = (ballSpeed - ballXComponentLowerBound) * Math.random();
														o.motion[0] = (ballXComponentLowerBound + randomXComponent) * Math.signum(-o.motion[0]);
														o.motion[1] = (ballSpeed - randomXComponent - ballXComponentLowerBound) * Math.signum(-o.motion[1]);
													} else {
														o.motion[0] = -o.motion[0];
													}
													o.x = p.getPlayerObject().x + p.getPlayerObject().width;
													gameBall.updateCachedMotionData(gameBall);
													if (cinst.doGameSounds) {
														//Play deflection sound
														gameSounds.playGameSound(GameSoundEffectManager.WALL_IMPACT);
													}
													break;
												case PhysicalObject.BOTTOM:
													if (cinst.randomBallDeflection) {
														randomXComponent = (ballSpeed - ballXComponentLowerBound) * Math.random();
														o.motion[0] = (ballXComponentLowerBound + randomXComponent) * Math.signum(-o.motion[0]);
														o.motion[1] = (ballSpeed - randomXComponent - ballXComponentLowerBound) * Math.signum(-o.motion[1]);
													} else {
														o.motion[1] = -o.motion[1];
													}
													o.y = p.getPlayerObject().y + p.getPlayerObject().height;
													gameBall.updateCachedMotionData(gameBall);
													if (cinst.doGameSounds) {
														//Play deflection sound
														gameSounds.playGameSound(GameSoundEffectManager.WALL_IMPACT);
													}
													break;
												case PhysicalObject.LEFT:
													if (cinst.randomBallDeflection) {
														randomXComponent = (ballSpeed - ballXComponentLowerBound) * Math.random();
														o.motion[0] = (ballXComponentLowerBound + randomXComponent) * Math.signum(-o.motion[0]);
														o.motion[1] = (ballSpeed - randomXComponent - ballXComponentLowerBound) * Math.signum(-o.motion[1]);
													} else {
														o.motion[0] = -o.motion[0];
													}
													o.x = p.getPlayerObject().x - o.width;
													gameBall.updateCachedMotionData(gameBall);
													if (cinst.doGameSounds) {
														//Play deflection sound
														gameSounds.playGameSound(GameSoundEffectManager.WALL_IMPACT);
													}
													break;
											}
										}
									}
								}
								if (playingWithAI && !Player.areAllPlayersPaused()) {
									AIAction(AIPlayer);
									if (gameAIIntelligenceLevel == AIModes.AI_VECTOR) {
										if (System.currentTimeMillis() >= nextButterflyTime && nextButterflyTime != -1 && cinst.getNumPauseReasons() == 0) {
											//Pause counter until further notice (current butterfly finishes)
											cinst.nextButterflyTime = -1;
											//Display the butterfly
											cinst.displayButterfly();
										}
									}
								}
								if (doBallTrailEffect) {
									//Array shift and draw ball blurs
									for (int i = 3; i < ballTrailCoords.length; i++) {
										ballTrailCoords[i-3] = ballTrailCoords[i];
									}
									if (doRandomTrailPositions) {
										ballTrailCoords[ballTrailCoords.length - 3] = (int) (Math.random() * gameBall.motion[0]);
										ballTrailCoords[ballTrailCoords.length - 2] = (int) (Math.random() * gameBall.motion[1]);
										int cachedNum = ballTrailCoords[ballTrailCoords.length - 3];
										double randomRadian = Math.random() * 2 * Math.PI;
										ballTrailCoords[ballTrailCoords.length - 3] = (int) (gameBall.x + cachedNum * Math.cos(randomRadian) - ballTrailCoords[ballTrailCoords.length - 2] * Math.sin(randomRadian));
										ballTrailCoords[ballTrailCoords.length - 2] = (int) (gameBall.y + cachedNum * Math.sin(randomRadian) + ballTrailCoords[ballTrailCoords.length - 2] * Math.cos(randomRadian));
									} else {
										ballTrailCoords[ballTrailCoords.length - 3] = (int) gameBall.x;
										ballTrailCoords[ballTrailCoords.length - 2] = (int) gameBall.y;
									}
									if (ballTrailType == BALL_TRAIL) {
										ballTrailCoords[ballTrailCoords.length - 1] = 0;
									} else if (ballTrailType == STAR_TRAIL) {
										ballTrailCoords[ballTrailCoords.length - 1] = (1 * 16777216) + ((int) (Math.random() * 3)) * 65536;
									}
								}
							}
							if (autoResetScene && Math.abs(gameBall.motion[0]) <= ballXComponentLowerBound && !Player.areAllPlayersPaused()) {
								resetScene("Ball stuck/too slow!", Color.RED);
							}
							if (cinst.doingButterfly && cinst.getNumPauseReasons() == 0 & cinst.butterflyImage != null) {
								cinst.butterflyImageCachedX++;
								cinst.butterflyImage.setX(cinst.butterflyImageCachedX);
								cinst.butterflyImage.setY(cinst.butterflyImageCachedY + Math.sin(cinst.butterflyImageCachedX * (Math.PI/180)) * 100);
								if (cinst.butterflyImage.getX() + cinst.butterflyImage.getWidth() >= cinst.getWidth()) {
									//Stop the animation
									cinst.doingButterfly = false;
									//Set time for next butterfly
									cinst.nextButterflyTime = (long) (System.currentTimeMillis() + Math.random() * 120000);
									//Hide the butterfly image
									//No need to use synchronized(cinst.butterflyImage) {} here - this code is executed on AWT event dispatch thread
									cinst.butterflyImage.setVisible(false);
									//DO NOT destroy/nullify the reference to the butterfly image
								}
							}
						}
						Thread.sleep((long) (1.0/fps * 1000));
					}
				} catch (InterruptedException ie) {
					ie.printStackTrace();
				}
				System.out.println("Physics thread finished");
			}
		};
		this.playerThread = new Thread() {
			@Override
			public void run() {
				Player currentPlayer;
				try {
					while (true) {
						if (!cinst.lockAllObjects) {
							for (int i = 0; i < Player.getRegisteredPlayers().size(); i++) {
								currentPlayer = Player.getRegisteredPlayers().get(i);
								switch (currentPlayer.currentAction) {
									case Player.UP:
										if (currentPlayer.getY() >= 5 && !Player.areAllPlayersPaused()) {
											currentPlayer.setY(Math.max(currentPlayer.getY() - Player.playerSpeed, 5));
										}
										break;
									case Player.DOWN:
										if (currentPlayer.getY() + currentPlayer.getHeight() <= currentPlayer.panel.getHeight() - 5 && !Player.areAllPlayersPaused()) {
											currentPlayer.setY(Math.min(currentPlayer.getY() + Player.playerSpeed, currentPlayer.panel.getHeight() - currentPlayer.getHeight() - 5));
										}
										break;
									case Player.NONE:
										break;
									default:
								}
								//System.out.println(currentPlayer.currentAction + ", " + currentPlayer.getPlayerObject().motion[1]);
							}
						}
						Thread.sleep((long) (1.0/fps * 1000));
					}
				} catch (InterruptedException ie) {
					ie.printStackTrace();
				}
				System.out.println("Player action handler thread finished");
			}
		};
		this.playerThread.start();
		this.audioThread = new AudioThread() {
			boolean isPlayerActiveYet;
			@Override
			public void run() {
				while (!this.isInterrupted()) {
					try {
						if (cinst.doBgAudio && !this.isAudioThreadPaused()) {
							InputStream istream;
							if (cinst.playingWithAI) {
								switch (cinst.gameAIIntelligenceLevel) {
									case AI_STUPID: {
										byte random = (byte) (Math.random() * 2);
										switch (random) {
											case 0:
												istream = getClass().getResourceAsStream("media/chaseMusic.mp3");
												break;
											case 1:
												istream = getClass().getResourceAsStream("media/tranquilMusic.mp3");
												break;
											default:
												istream = null;
										}
										break;
									}
									case AI_TYPICAL: {
										istream = getClass().getResourceAsStream("media/gameSoundNormal.mp3");
										break;
									}
									case AI_VECTOR: {
										istream = getClass().getResourceAsStream("media/gameSoundHard.mp3");
										break;
									}
									default:
										istream = null;
								}
							} else {
								istream = getClass().getResourceAsStream("media/gameSoundMultiplayer.mp3");
							}
							isPlayerActiveYet = false;
							//Always play the audio on another thread - this thread must busy-wait to check restart/interruption signals
							blockingTasksPool.submit(new Runnable() {
								@Override
								public void run() {
									try {
										isPlayerActiveYet = true;
										audioPlayer.startStreaming(istream, new Runnable() {
											@Override
											public void run() {
												//Set volume control - MASTER_GAIN FloatControl accepts decibels (dB), which are a LOGARTHMIC scale. Use appropriate expression to convert linear to base 10 logarithm
												((FloatControl) audioPlayer.getControl(FloatControl.Type.MASTER_GAIN)).setValue((float) (20 * Math.log(cinst.gameBgAudioVolume/100.0f)/Math.log(10)));
												System.out.println(((FloatControl) audioPlayer.getControl(FloatControl.Type.MASTER_GAIN)).getValue());
											}
										});
									} catch (UnsupportedAudioFileException uafe) {
										uafe.printStackTrace();
									} catch (LineUnavailableException lue) {
										lue.printStackTrace();
									} catch (IOException ioe) {
										ioe.printStackTrace();
									}
								}
							});
							while (!isPlayerActiveYet && !isInterrupted()) {}
							while (!shouldRestart() && audioPlayer.isActive() && !isInterrupted()) {
							}
							if (this.shouldRestart()) {
								try {
									//If the audio thread should be paused, the restart flag should still be reset; it was going to do so anyway, except now a pause will ensue before the track restarts
									audioPlayer.stop();
									resetRestartState();
								} catch (IOException ioe) {
									ioe.printStackTrace();
								} catch (IllegalStateException ise) {
									ise.printStackTrace();
									//Player stopped by other means; must still reset state
									resetRestartState();
								}
							}
						} else {
							//this refers to the current thread. Call notify on it (derived from java.lang.Object) to wake the thread and play audio
							synchronized(this) {
								this.wait();
							}
						}
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
				System.out.println("Audio thread finished");
			}
			//Custom implementation of interrupt for this audioThread - internally stop execution
			@Override
			public void interrupt() {
				//Must effectively add functionality, not completely redefine it!
				super.interrupt();
				//Wake the possibly sleeping thread (audio waiting to be played)
				synchronized(this) {
					this.notifyAll();
				}
				//Stop the audio player
				try {
					audioPlayer.stop();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				} catch (IllegalStateException ise) {
					//This would be executed due to the player not being active - no problem
					ise.printStackTrace();
				}
			}

			@Override
			public void setAudioThreadPaused(boolean paused) {
				super.setAudioThreadPaused(paused);
				if (!paused) {
					synchronized(this) {
						//The player is most probably paused before starting
						this.notifyAll();
					}
				}
			}
		};
		//No longer needed
		/*frame.removeWindowListener(currentWindowListener);
		currentWindowListener = new WindowListener() {
			public void windowDeactivated(WindowEvent e) {
			}
			public void windowActivated(WindowEvent e) {
			}
			public void windowDeiconified(WindowEvent e) {
			}
			public void windowIconified(WindowEvent e) {
			}
			public void windowClosed(WindowEvent e) {
			}
			public void windowClosing(WindowEvent e) {
				//Free all resources
				stopEverything();
			}
			public void windowOpened(WindowEvent e) {
			}
		};
		frame.addWindowListener(currentWindowListener);*/
		TemporaryPlayer currentTemplate;
		for (int i = 0; i < TemporaryPlayer.getRegisteredTemplates().size(); i++) {
			currentTemplate = TemporaryPlayer.getRegisteredTemplates().get(i);
			currentTemplate.playerSide = i;
			currentTemplate.createPlayer();
		}
		//startPlaying method must be invoked AFTER the template initialisation to set default state for all the players
		startPlaying(ballSpeed, doAI, ballColor, playerSpeed, randomBallDeflection);
		//Start physics thread AFTER game ball position is set, to avoid the player on the other side getting a free point
		//Start physics thread AFTER initial count in order to prevent it from stopping the timer before it even starts
		this.physicsThread.start();
		//Start the audio thread AFTER startPlaying, to avoid race conditions when it comes to defining the track to play based on playingWithAI
		this.audioThread.start();
		//Possibly set the butterfly time at the end, as to avoid delaying the invocation of the other thread starting methods
		if (gameAIIntelligenceLevel == AIModes.AI_VECTOR) {
			this.nextButterflyTime = (long) (System.currentTimeMillis() + Math.random() * 120000);
		}
	}

	public void startPlaying(double ballSpeed, boolean doAI, Color ballColor, double playerSpeed, boolean randomBallDeflection) {
		// Set paused and restarting state to false - restarting game!
		this.paused = false;
		this.restarting = false;
		this.resettingScene = false;
		this.resuming = false;
		this.pauseBtn.setText("Pause...");

		//Set all game state to default - restarting game
		this.hasDeclaredVictor = false;
		this.finalGameAction = false;
		//Reset array of ball trail coordinates if applicable
		if (doBallTrailEffect) {
			ballTrailCoords = new int[3 * ballTrailAmount];
		}
		//(Re)set competition-mode specific state (e.g.: Remaining time field text)
		if (competitionMode == PingPongGame.CompetitionMode.TIME_COMPETITION) {
			gameObjectiveCounter.isVisible = true;
			gameObjectiveCounter.key = "Remaining time";
			gameObjectiveCounter.value = TimeUtils.toFormattedTime(gameTimeLimit);
		} else if (competitionMode == PingPongGame.CompetitionMode.TARGET_COMPETITION) {
			gameObjectiveCounter.isVisible = true;
			gameObjectiveCounter.key = "Score target";
			gameObjectiveCounter.value = Integer.toString(scoreTarget);
		} else if (competitionMode == PingPongGame.CompetitionMode.FREE_PLAY) {
			gameObjectiveCounter.isVisible = false;
			gameObjectiveCounter.key = "";
			gameObjectiveCounter.value = "";
		}
		this.gameBall.color = ballColor;
		this.ballSpeed = ballSpeed;
		this.playerSpeed = playerSpeed;
		this.playingWithAI = doAI;
		this.randomBallDeflection = randomBallDeflection;
		//Realistic energy absorption:
		//this.gameBall.velocityChangeOnCollision = 0.5f;
		this.gameBall.ignoreGravity = !quadraticBallMotion;
		Player.playerSpeed = playerSpeed;
		if (doAI) {
			if (this.gameAIIntelligenceLevel == AIModes.AI_VECTOR) {
				//Set time for next butterfly
				cinst.nextButterflyTime = (long) (System.currentTimeMillis() + Math.random() * 120000);
			}
			//(Re)create the AIPlayer should there not be one
			if (AIPlayer == null) {
				AIPlayer = new Player(new Rectangle(this.getWidth() - 40, this.getHeight()/2 - 50, 20, 100, new Color(0, 255, 255)), 0, "Larry the AI", cinst, new HashMap<KeyStroke, Integer>(), cinst.getWidth() - 20, 20, true, TemporaryPlayer.RIGHT);
			}
		}
		//Set all players' default state - DO NOT recreate them since they were created in setGameEnvironment()
		for (Player player : Player.getRegisteredPlayers()) {
			//Reset all players
			player.score = 0;
		}
		try {
			resetScene("", Color.BLACK);
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
		this.lockAllObjects = false;
		if (competitionMode == CompetitionMode.TIME_COMPETITION) {
			gameTimer.start(gameTimeLimit * 1000);
		}
	}
	public void AIAction(Player AIPlayer) {
		//Number of oscillations to ignore
		//System.out.println(/*gameBall.x - frame.getWidth()/(Math.abs(gameBall.motion[0]/gameBall.motion[1]) * frame.getHeight())*/ bottomXLineToEnd(gameBall.motion[1]/gameBall.motion[0], frame.getWidth(), frame.getHeight(), gameBall.x, gameBall.y));
		switch (gameAIIntelligenceLevel) {
			case AI_STUPID:
				
				break;
			case AI_TYPICAL:
				//Blindly chase the ball's y-component
				if (gameBall.y + gameBall.height/2 > AIPlayer.getY() + AIPlayer.getHeight()/2) {
					AIPlayer.setY(Math.max(AIPlayer.getY() + Player.playerSpeed, 5));
				} else if (gameBall.y + gameBall.height < AIPlayer.getY() + AIPlayer.getHeight()/2) {
					AIPlayer.setY(Math.min(AIPlayer.getY() - Player.playerSpeed, cinst.getHeight() - 5 - AIPlayer.getHeight()));
				}
				break;
			case AI_VECTOR:
				//Predict the ball's position and go there!
				double[] ballCoords = new double[] {gameBall.x, gameBall.y};
				double[] ballMotion = new double[] {gameBall.motion[0], gameBall.motion[1]};
				if (this.doingButterfly) {
					//Follow the butterfly if there is one!
					if (this.butterflyImage.getY() - 5 > AIPlayer.getY()) {
						AIPlayer.setY(Math.max(AIPlayer.getY() + Player.playerSpeed, 5));
					} else if (this.butterflyImage.getY() + 5 < AIPlayer.getY()) {
						AIPlayer.setY(Math.max(AIPlayer.getY() - Player.playerSpeed, 5));
					}
				} else if (ballMotion[0] > 0 /*AI player always on right side*/) {
					while (true) {
						ballCoords[0] += ballMotion[0];
						ballCoords[1] += ballMotion[1];
						if (!(ballMotion[0] == 0 && ballMotion[1] == 0)) {
							ballMotion[0] += (gameBall.acceleration/fps) * (Math.atan(ballMotion[1]/ballMotion[0]) * (180/Math.PI))/90;
							if (gameBall.ignoreGravity) {
								ballMotion[1] += (gameBall.acceleration/fps) * (90 - Math.atan(ballMotion[1]/ballMotion[0]) * (180/Math.PI))/90;
							} else {
								ballMotion[1] += (gameBall.acceleration/fps) * (90 - Math.atan(ballMotion[1]/ballMotion[0]) * (180/Math.PI))/90 + gravity/fps;
							}
						}
						if (ballCoords[1] + gameBall.height >= getHeight()) {
							ballCoords[1] = getHeight() - gameBall.height;
							ballMotion[1] = -Math.abs(ballMotion[1]) * (1.0f - gameBall.velocityChangeOnCollision);
						} else if (ballCoords[1] <= 0) {
							ballCoords[1] = 0;
							ballMotion[1] = Math.abs(ballMotion[1]) * (1.0f - gameBall.velocityChangeOnCollision);
						}
						if (ballMotion[0] < 0 && ballCoords[0] <= Player.getRegisteredPlayers().get(0).getX() + Player.getRegisteredPlayers().get(0).getWidth()) {
							break;
						} else if (ballMotion[0] > 0 && ballCoords[0] + gameBall.width >= Player.getRegisteredPlayers().get(1).getX()) {
							break;
						}
					}
				} else {
					ballCoords[1] = AIPlayer.getY() + (AIPlayer.getHeight() - gameBall.height)/2;
				}
				if (ballCoords[1] + gameBall.height/2 > AIPlayer.getY() + AIPlayer.getHeight()/2) {
					AIPlayer.setY(Math.max(AIPlayer.getY() + Player.playerSpeed, 5));
				} else if (ballCoords[1] + gameBall.height < AIPlayer.getY() + AIPlayer.getHeight()/2) {
					AIPlayer.setY(Math.min(AIPlayer.getY() - Player.playerSpeed, cinst.getHeight() - 5 - AIPlayer.getHeight()));
				}
				break;
			default:
		}
	}
}

/**
 * A generic class representing an option of a menu. Has an in-menu text representation and a value (of the specified generic type). The selected {@code ComboItem<T>} would correspond to a value from a text representation
 * @version 1.0
*/
class ComboItem<T> {
	String text;
	T value;
	public ComboItem(String text, T value) {
		this.text = text;
		this.value = value;
	}
	@Override
	public String toString() {
		return text;
	}
	@Override
	public int hashCode() {
		return this.value.toString().hashCode();
	}
	public boolean equals(Object o) {
		try {
			if (this.value.toString().hashCode() == ((ComboItem<?>) o).value.toString().hashCode()) {
				return true;
			} else {
				return false;
			}
		} catch (ClassCastException cce) {
			return false;
		}
	}
}

/**
 * The abstract base class for all physical objects (e.g.: square, circle)
*/
abstract class PhysicalObject {
	/**
	 * <p>One of five (5) collision types:</p>
	 * <p>{@code TOP} - Object collides with upper side</p>
	 * <p>{@code RIGHT} - Object collides with right side</p>
	 * <p>{@code BOTTOM} - Object collides with bottom side</p>
	 * <p>{@code LEFT} - Object collides with left side</p>
	 * <p>{@code NONE} represents no collision</p>
	 */
	public static final int NONE = -1, TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;
	//A LinkedList of registered PhysicalObjects
	private static LinkedList<PhysicalObject> registeredObjects = new LinkedList<PhysicalObject>();
	//Object's x, y, width and height
	double x, y, width, height;
	//Object's cached position (deprecated, marked for removal, redundant)
	/**
	 * Stores a {@code PhysicalObject}'s cached X and Y positions for purposes such as clever AI ball position computation through vectors
	 * @deprecated This attribute is redundant as a more reliable strategy has been found
	 */
	double[] cachedCoords = new double[2];
	/**
	 * Stores a {@code PhysicalObject}'s cached X and Y motion vectors for purposes such as clever AI ball position computation through vectors
	 * @deprecated This attribute is redundant as a more reliable strategy has been found
	 */
	double[] cachedMotion = new double[2];
	/**
	 * Stores the Object's motion as a vector with an x ({@code cachedMotion[0]}) component and a y ({@code cachedMotion[1]}) component. Defaults to (0, 0)
	 */
	double[] motion = new double[] {0, 0};
	/**
	 * Stores the object's acceleration (dv[x]/dt, dv[y]/dt)
	*/
	double acceleration = 0;
	/**
	 * A ratio from 0 to 1 determining the percentage of velocity to lose on collision. Zero by default
	*/
	float velocityChangeOnCollision = 0f;
	/**
	 * Whether or not this {@code PhysicalObject} should be influenced by gravity
	*/
	boolean ignoreGravity = true;
	/**
	 * This PhysicalObject's colour
	 */
	Color color;
	/**
	 * The physical object's constructor. Used for instantiating one and adding it to an internal list
	 * @param x The current object's x-position
	 * @param y The current object's y-position
	 * @param width The current object's width
	 * @param height The current object's height
	 * @param color The current object's colour
	*/
	public PhysicalObject(double x, double y, double width, double height, Color color) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.color = color;
		registeredObjects.add(this);
	}
	/**
	 * The getter method for the LinkedList which stores all registered {@code PhysicalObjects}
	 * @return The {@code LinkedList<PhysicalObject>} storing all registered {@code PhysicalObjects}
	 */
	public static LinkedList<PhysicalObject> getRegisteredObjects() {
		return registeredObjects;
	}
	/**
	 * A method to update the current's object's cached coordinates and motion to those of the PhysicalObject specified
	 * @param object The PhysicalObject containing the {@code cachedCoords} and {@code cachedMotion} to set the current object's corresponding and respective fields to
	 * @deprecated This method, along with the {@code cachedCoords} and {@code cachedMotion} are redundant and marked for removal
	 */
	public void updateCachedMotionData(PhysicalObject object) {
		this.cachedCoords[0] = object.x;
		this.cachedCoords[1] = object.y;
		this.cachedMotion[0] = object.motion[0];
		this.cachedMotion[1] = object.motion[1];
	}
	/**
	 * Removes the object from the internal list of registered objects. The object's absence from the list will return false
	 * @return A boolean determining the success of the operation. {@code true} refers to success, whereas {@code false} refers to failure
	 */
	public boolean remove() {
		return registeredObjects.remove(this);
	}
	/**
	 * A method to check whether a {@code PhysicalObject} is touching another
	 * @param entity The entity to test for compared to the current instance
	 * @return One of two boolean results - {@code true} if they are touching or {@code false} otherwise
	 */
	public boolean isTouching(PhysicalObject entity) {
		return (entity.x < this.x + this.width && this.x < entity.x + entity.width) &&
			   (entity.y < this.y + this.height && this.y < entity.y + entity.height);
	}
	/**
	 * A method to get collision data in more detail than {@code isTouching(PhysicalObject)}
	 * @param entity The entity to test for compared to the current instance
	 * @return One of five integers from {@code PhysicalObject.TOP}, {@code PhysicalObject.BOTTOM}, {@code PhysicalObject.LEFT}, {@code PhysicalObject.RIGHT} and {@code PhysicalObject.NONE}
	 */
	public int getCollisionData(PhysicalObject entity) {
		//Array with x and y components of net motion in the entity parameter's perspective
		double[] netMotionWRTEntity = {this.motion[0] - entity.motion[0], this.motion[1] - entity.motion[1]};
		if (this.isTouching(entity)) {
			if (entity.x < this.x + this.width && netMotionWRTEntity[0] < 0) {
				return PhysicalObject.RIGHT;
			} else if (this.x < entity.x + entity.width && netMotionWRTEntity[0] > 0) {
				return PhysicalObject.LEFT;
			} else if (entity.y < this.y + this.height && netMotionWRTEntity[1] > 0) {
				return PhysicalObject.BOTTOM;
			} else if (this.y < entity.y + entity.height && netMotionWRTEntity[1] < 0) {
				return PhysicalObject.TOP;
			} else {
				return PhysicalObject.NONE;
			}
		} else {
			return PhysicalObject.NONE;
		}
	}
}
/**
 * A subclass of {@code PhysicalObject}. Draws an oval of customisable x and y radii
 */
class Oval extends PhysicalObject {
	public Oval(double x, double y, double width, double height, Color color) {
		super(x, y, width, height, color);
	}
	public boolean isTouching(Oval entity) {
		if (super.isTouching(entity)) {
			return true;
		} else {
			return false;
		}
	}
}
/**
 * A subclass of {@code PhysicalObject}. Represents an abstract quadrilateral (could be a rectangle, trapezium, etc...)
 */
abstract class Quadrilateral extends PhysicalObject {
	public Quadrilateral(double x, double y, double width, double height, Color color) {
		super(x, y, width, height, color);
	}
}

/**
 * A subclass of {@code Quadrilateral}. Draws a rectangle of customisable width and height
 */
class Rectangle extends Quadrilateral {
	public Rectangle(double x, double y, double width, double height, Color color) {
		super(x, y, width, height, color);
	}
}

/**
 * A class representing a player, with a {@code PhysicalObject} representation and {@code AbstractActions} to be bound to key presses, to name a few
*/
class Player implements java.io.Serializable {
	private static final Object allPlayersPausedLock = new Object();
	public static double playerSpeed = 0;
	public static final int NONE = -1, UP = 0, DOWN = 1;
	private static LinkedList<Player> registeredPlayers = new LinkedList<Player>();
	private static Boolean allPlayersPaused = false;
	private PhysicalObject playerObj;
	private Player thisPlayer = this;
	private String UUID;
	int currentAction = Player.NONE;
	PingPongGame panel;
	//These player actions should NOT be static
	/**
	 * {@code AbstractAction}s representing player actions
	 * <p>{@code upAction} refers to moving the player upwards</p>
	 * <p>{@code downAction} refers to moving the player downwards</p>
	 * <p>{@code releaseAction} refers to releasing the key in question, generally stopping the player in its tracks</p>
	 */
	public AbstractAction upAction = new AbstractAction() {
		public void actionPerformed(ActionEvent e) {
			thisPlayer.currentAction = Player.UP;
		}
	}, downAction = new AbstractAction() {
		public void actionPerformed(ActionEvent e) {
			thisPlayer.currentAction = Player.DOWN;
		}
	}, releaseAction = new AbstractAction() {
		public void actionPerformed(ActionEvent e) {
			thisPlayer.currentAction = Player.NONE;
		}
	};
	boolean visible = true, scoreVisible = true, scoreOppSide;
	int score, scoreX, scoreY, side;
	private static int i = 0;
	private final Object o = new Object();
	private LinkedList<Integer> ownedBindingIds = new LinkedList<Integer>();
	String name;
	HashMap<KeyStroke, AbstractAction> bindings;
	public PhysicalObject getPlayerObject() {
		return playerObj;
	}
	//Add a number of setter (mutator) methods along with their corresponding getter methods for the player object; Pausing restrictions should NOT be implemented here - other code might still try to reposition objects for non-game reasons
	public void setX(double x) {
		this.playerObj.x = x;
	}
	public void setY(double y) {
		this.playerObj.y = y;
	}
	public double getX() {
		return this.playerObj.x;
	}
	public double getY() {
		return this.playerObj.y;
	}
	public void setWidth(double width) {
		this.playerObj.width = width;
	}
	public void setHeight(double height) {
		this.playerObj.height = height;
	}
	public double getWidth() {
		return this.playerObj.width;
	}
	public double getHeight() {
		return this.playerObj.height;
	}
	//Pausing all players
	public static void pauseAllPlayers() {
		synchronized(allPlayersPausedLock) {
			allPlayersPaused = true;
		}
	}
	//Resuming all players
	public static void resumeAllPlayers() {
		synchronized(allPlayersPausedLock) {
			allPlayersPaused = false;
		}
	}
	public synchronized static boolean areAllPlayersPaused() {
		return allPlayersPaused;
	}
	public String getUUID() {
		return this.UUID;
	}
	public Player(PhysicalObject playerObj, int score, String name, PingPongGame panel, HashMap<KeyStroke, Integer> bindings, int scoreX, int scoreY, boolean scoreOppSide, int side) {
		this.playerObj = playerObj;
		this.score = score;
		this.name = name;
		this.panel = panel;
		this.scoreX = scoreX;
		this.scoreY = scoreY;
		this.scoreOppSide = scoreOppSide;
		this.side = side;
		//Randomly generate player UUID
		this.UUID = java.util.UUID.randomUUID().toString();
		//Map to bind KeyStrokes to AbstractActions
		HashMap<KeyStroke, AbstractAction> newMap = new HashMap<KeyStroke, AbstractAction>();
		for (KeyStroke binding : bindings.keySet()) {
			if (bindings.get(binding) == Player.UP) {
				newMap.put(binding, upAction);
			} else if (bindings.get(binding) == Player.DOWN) {
				newMap.put(binding, downAction);
			}
		}
		this.bindings = newMap;
		for (KeyStroke value : this.bindings.keySet()) {
			this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(value, i);
			this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(value.getKeyCode(), 0, true), o);
			this.panel.getActionMap().put(i, this.bindings.get(value));
			ownedBindingIds.add(i);
			i++;
		}

		this.panel.getActionMap().put(o, releaseAction);
		synchronized(registeredPlayers) {
			registeredPlayers.add(this);
		}
		/*this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
		this.panel.getActionMap().put("right", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				playerObj.x += 1;
			}
		});
		this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
		this.getActionMap().put("left", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				mainOval.x -= 1;
			}
		});
		this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
		this.getActionMap().put("up", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				mainOval.y -= 1;
			}
		});
		this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
		this.getActionMap().put("down", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				mainOval.y += 1;
			}
		});*/
	}
	public Player(PhysicalObject playerObj, int score, String name, PingPongGame panel, HashMap<KeyStroke, Integer> bindings, int scoreX, int scoreY, boolean scoreOppSide, int side, String UUID) {
		this.playerObj = playerObj;
		this.score = score;
		this.name = name;
		this.panel = panel;
		this.scoreX = scoreX;
		this.scoreY = scoreY;
		this.scoreOppSide = scoreOppSide;
		this.side = side;
		this.UUID = UUID;
		//Map to bind KeyStrokes to AbstractActions
		HashMap<KeyStroke, AbstractAction> newMap = new HashMap<KeyStroke, AbstractAction>();
		for (KeyStroke binding : bindings.keySet()) {
			if (bindings.get(binding) == Player.UP) {
				newMap.put(binding, upAction);
			} else if (bindings.get(binding) == Player.DOWN) {
				newMap.put(binding, downAction);
			}
		}
		this.bindings = newMap;
		for (KeyStroke value : this.bindings.keySet()) {
			this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(value, i);
			this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(value.getKeyCode(), 0, true), o);
			this.panel.getActionMap().put(i, this.bindings.get(value));
			ownedBindingIds.add(i);
			i++;
		}

		this.panel.getActionMap().put(o, releaseAction);
		synchronized(registeredPlayers) {
			registeredPlayers.add(this);
		}
		/*this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
		this.panel.getActionMap().put("right", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				playerObj.x += 1;
			}
		});
		this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
		this.getActionMap().put("left", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				mainOval.x -= 1;
			}
		});
		this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
		this.getActionMap().put("up", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				mainOval.y -= 1;
			}
		});
		this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
		this.getActionMap().put("down", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				mainOval.y += 1;
			}
		});*/
	}
	public void remove() {
		javax.swing.InputMap inputMap = this.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		for (int i : ownedBindingIds) {
			this.panel.getActionMap().remove(i);
			i++;
		}
		for (KeyStroke value : this.bindings.keySet()) {
			inputMap.remove(value);
			inputMap.remove(KeyStroke.getKeyStroke(value.getKeyCode(), 0, true));
		}
		this.panel.getActionMap().remove(o);
		registeredPlayers.remove(this);
		playerObj.remove();
	}
	public static LinkedList<Player> getRegisteredPlayers() {
		return registeredPlayers;
	}
}
class TemporaryPlayer {
	public static final int LEFT = 0, RIGHT = 1;
	private static LinkedList<TemporaryPlayer> registeredTemplates = new LinkedList<TemporaryPlayer>();
	int playerSide;
	String name;
	PingPongGame inst;
	HashMap<KeyStroke, Integer> bindings;
	Color color;
	private boolean alreadyConvertedToPlayer = false;
	JPanel indicatorPanel;
	public TemporaryPlayer(String name, HashMap<KeyStroke, Integer> bindings, Color color, PingPongGame inst) {
		this.name = name;
		this.bindings = bindings;
		this.color = color;
		this.inst = inst;
		registeredTemplates.add(this);
	}
	public static LinkedList<TemporaryPlayer> getRegisteredTemplates() {
		return registeredTemplates;
	}
	public void remove() {
		registeredTemplates.remove(this);
	}
	public Player createPlayer() throws IllegalStateException {
		if (alreadyConvertedToPlayer) {
			throw new IllegalStateException("Cannot re-create a player from the same player template");
		} else {
			alreadyConvertedToPlayer = true;
			if (playerSide == TemporaryPlayer.LEFT) {
				return new Player(new Rectangle(20, 40, 20, 100, color), 0, name, inst, bindings, 20, 20, false, TemporaryPlayer.LEFT);
			} else if (playerSide == TemporaryPlayer.RIGHT) {
				return new Player(new Rectangle(inst.getWidth() - 40, inst.getHeight()/2 - 50, 20, 100, color), 0, name, inst, bindings, inst.getWidth() - 20, 20, true, TemporaryPlayer.RIGHT);
			}
		}
		return null;
	}
}

/**
 * <p>An enum for menus to represent the possible states:</p>
 * <p>{@code PENDING_RESPONSE} if still waiting for user response</p>
 * <p>{@code OK} representing success</p>
 * <p>{@code CANCEL} representing failure (aborted menu)</p>
*/
enum MenuResponseStates {
	/**
	 * Represents the state of waiting for a user's response
	*/
	PENDING_RESPONSE,
	/**
	 * Represents the state of a menu's success - Changes can be saved, for instance
	*/
	OK,
	/**
	 * Represents the state of a menu's failure (aborted menu) - Changes should not be saved, for instance
	*/
	CANCEL
}