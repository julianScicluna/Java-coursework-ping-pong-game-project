package Helpers.AudioHelpers;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;


import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * An abstraction layer over JavaSound to play audio from an {@code java.io.InputStream} or {@code java.io.File} (access to media), either as a stream or by loading the file into memory and playing it (typically better for shorter clips which must be played as quickly as possible)
 * @version 1.0
*/
public class AudioPlayer {
	static int NONE = 0, STREAM_PLAYBACK = 1, MEMORY_PLAYBACK = 2, PREDEFINED_CLIP_MEMORY_PLAYBACK = 3;

	protected boolean paused = false, active = false, loop = false, audioFileLoaded = false;
	protected final Object lockObj = new Object();
	private AudioInputStream in, din;
	protected SourceDataLine line;
	protected int playbackType = NONE;
	protected long playbackPausePosition = 0;
	private AudioFormat loadedAudioFileDataFormat;
	private byte[] audioData, loadedAudioFileData;

	/**
	 * A HIDDEN method, available only to derived instances of the {@code AudioPlayer} class to set the player's state in order to play a track. Does not apply when sending prespecified clips
	 * @param file
	 * @return
	 * @throws UnsupportedAudioFileException
	 * @throws LineUnavailableException
	 * @throws IOException
	 */

	//Code adapted from https://docs.oracle.com/javase/tutorial/sound/playing.html
	protected AudioFormat initialisePlayer(File file) throws UnsupportedAudioFileException, LineUnavailableException, IOException {
		this.in = AudioSystem.getAudioInputStream(file);
		AudioFormat baseFormat = in.getFormat();
		AudioFormat decodedFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
			baseFormat.getChannels() * 2, baseFormat.getSampleRate(),
			false
		);
		this.din = AudioSystem.getAudioInputStream(decodedFormat, this.in);
		return decodedFormat;
	}

	//This is an initialisation method - DO NOT set any internal state other than streams and formats
	protected AudioFormat initialisePlayer(InputStream istream) throws UnsupportedAudioFileException, LineUnavailableException, IOException {
		this.in = AudioSystem.getAudioInputStream(new BufferedInputStream(istream));
		AudioFormat baseFormat = in.getFormat();
		AudioFormat decodedFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
			baseFormat.getChannels() * 2, baseFormat.getSampleRate(),
			false
		);
		this.din = AudioSystem.getAudioInputStream(decodedFormat, this.in);
		return decodedFormat;
	}

	/*protected static AudioFormatStreamGroup initialisePlayerForClip(File file) throws UnsupportedAudioFileException, LineUnavailableException, IOException {
		AudioInputStream in = AudioSystem.getAudioInputStream(file);
		AudioFormat baseFormat = in.getFormat();
		AudioFormat decodedFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
			baseFormat.getChannels() * 2, baseFormat.getSampleRate(),
			false
		);
		AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);
		return new AudioFormatStreamGroup(din, decodedFormat);
	}

	protected static AudioFormatStreamGroup initialisePlayerForClip(InputStream istream) throws UnsupportedAudioFileException, LineUnavailableException, IOException {
		AudioInputStream in = AudioSystem.getAudioInputStream(istream);
		AudioFormat baseFormat = in.getFormat();
		AudioFormat decodedFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
			baseFormat.getChannels() * 2, baseFormat.getSampleRate(),
			false
		);
		AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);
		return new AudioFormatStreamGroup(din, decodedFormat);
	}*/

	public boolean isLooping() {
		return loop;
	}
	public void setLooping(boolean loop) {
		this.loop = loop;
	}

	public boolean isActive() {
		return active;
	}
	public boolean isPaused() {
		return paused;
	}

	//DO NOT synchronise the start method - any other methods (namely the EDT, among others) which try to use/get a monitor on the object WILL be hung for the duration of the start method
	/**
	 * A method to play an audio file as a stream. Particularly useful for large audio files, which would be cumbersome, if not impossible to load into memory entirely.
	 * @param file The {@code java.io.File} to stream
	 * @param controlRunnable A {@code Runnable} to be executed (on the same thread) after audio initialisation yet prior to starting the audio. Useful for reading and modifying audio controls such as gain and reverb.
	 * @throws IOException
	 * @throws UnsupportedAudioFileException
	 * @throws LineUnavailableException
	 */
	public void startStreaming(File file, Runnable controlRunnable) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
		if (this.active) {
			throw new IllegalStateException("Cannot start an active player");
		} else {
			this.playbackType = STREAM_PLAYBACK;
			int nBytesRead;
			this.active = true;
			AudioFormat decodedFormat = initialisePlayer(file);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
			this.line = (SourceDataLine) AudioSystem.getLine(info);
			if (line != null) {
				line.open(decodedFormat);
				if (controlRunnable != null) {
					controlRunnable.run();
				}
				this.audioData = new byte[decodedFormat.getFrameSize() * Math.round(decodedFormat.getSampleRate() / 10)];
				this.clearAudioFile();
				// Start
				line.start();

				this.paused = false;
				do {
					while ((nBytesRead = din.read(this.audioData, 0, this.audioData.length)) != -1) {
						if (paused) {
							synchronized(this.lockObj) {
								try {
									this.lockObj.wait();
								} catch (InterruptedException ie) {
									ie.printStackTrace();
								}
							}
						} else if (!this.active) {
							//Stop method invoked during playback
							this.loop = false;
							break;
						}
						//Write the current state of the audio byte array to the SourceDataLine, which has similar properties to an output stream
						line.write(this.audioData, 0, nBytesRead);
					}
					if (this.loop) {
						//Reset streams, but keep the audio line!
						initialisePlayer(file);
					}
				} while (this.loop);
				//End of video stream/loop; must destroy the current player. Do so HERE AND ONLY HERE. Other methods should not do this themselves directly. They may only signal the start method to stop
				this.line.drain();
				this.line.stop();
				this.line.close();
				if (this.din != null) {
					this.din.close();
				}
				if (this.in != null) {
					this.in.close();
				}
			}
			this.stop();
		}
	}

	public void startStreaming(InputStream istream, Runnable controlRunnable) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
		if (this.active) {
			throw new IllegalStateException("Cannot start an active player");
		} else {
			this.playbackType = STREAM_PLAYBACK;
			int nBytesRead;
			this.active = true;
			AudioFormat decodedFormat = initialisePlayer(istream);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
			this.line = (SourceDataLine) AudioSystem.getLine(info);
			if (line != null) {
				line.open(decodedFormat);
				if (controlRunnable != null) {
					controlRunnable.run();
				}
				this.audioData = new byte[decodedFormat.getFrameSize() * Math.round(decodedFormat.getSampleRate() / 10)];
				this.clearAudioFile();
				// Start
				line.start();

				this.paused = false;
				do {
					while ((nBytesRead = din.read(this.audioData, 0, this.audioData.length)) != -1) {
						if (paused) {
							synchronized(this.lockObj) {
								try {
									this.lockObj.wait();
								} catch (InterruptedException ie) {
									ie.printStackTrace();
								}
							}
						} else if (!this.active) {
							//Stop method invoked during playback
							this.loop = false;
							break;
						}
						//Write the current state of the audio byte array to the SourceDataLine, which has similar properties to an output stream
						line.write(this.audioData, 0, nBytesRead);
					}
					if (this.loop) {
						//Reset streams, but keep the audio line!
						initialisePlayer(istream);
					}
				} while (this.loop);
				//End of video stream/loop; must destroy the current player. Do so HERE AND ONLY HERE. Other methods should not do this themselves directly. They may only signal the start method to stop
				this.line.drain();
				this.line.stop();
				this.line.close();
				if (this.din != null) {
					this.din.close();
				}
				if (this.in != null) {
					this.in.close();
				}
			}
			this.stop();
		}
	}

	public javax.sound.sampled.Control getControl(FloatControl.Type control) {
		if (this.playbackType == STREAM_PLAYBACK) {
			return this.line.getControl(control);
		} else if (this.playbackType == MEMORY_PLAYBACK || this.playbackType == PREDEFINED_CLIP_MEMORY_PLAYBACK) {
			//Maybe other mode-specific controls/conditions will be added here in the future
			return this.line.getControl(control);
		} else {
			throw new IllegalStateException("Cannot get controls of a nonexistent or inactive player");
		}
	}

	public javax.sound.sampled.Control[] getControls() {
		if (this.playbackType == STREAM_PLAYBACK) {
			return this.line.getControls();
		} else if (this.playbackType == MEMORY_PLAYBACK || this.playbackType == PREDEFINED_CLIP_MEMORY_PLAYBACK) {
			//Maybe other mode-specific controls/conditions will be added here in the future
			return this.line.getControls();
		} else {
			throw new IllegalStateException("Cannot get controls of a nonexistent or inactive player");
		}
	}

	/**
	 * A method which accepts a {@code java.io.File} to load an entire audio file into memory for enhanced performance during playback. The audio byte array is not returned, but stored internally for playing
	 * @param input The {@code java.io.File} representing the audio file to load into memory
	 * @throws LineUnavailableException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * @apinote This API should not be used for large media files, seeing as this method attempts to load the whole file into memory. This can result in significant performance degradation along with possible {@code OutOfMemoryErrors} for larger audio files.
	 */
	//Method to load an audio file in memory in the form of a byte array	
	public void loadAudioFile(File input) throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		if (this.audioFileLoaded) {
			throw new IllegalStateException("Cannot load an audio file into memory when one is already loaded. Must clear it first");
		} else {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int nBytesRead;
			AudioFormat decodedFormat = initialisePlayer(input);
			//Preserve this format for playing the file
			this.loadedAudioFileDataFormat = decodedFormat;
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
			this.line = (SourceDataLine) AudioSystem.getLine(info);

			if (this.line != null) {
				this.line.open(decodedFormat);
				//Used as a buffer to write to the byte array
				this.audioData = new byte[decodedFormat.getFrameSize() * Math.round(decodedFormat.getSampleRate() / 10)];
				// Start
				this.line.start();

				//audio streaming from audioData buffer
				while ((nBytesRead = this.din.read(this.audioData, 0, this.audioData.length)) != -1) {
					//Write the current state of the audio byte array to the SourceDataLine, which has similar properties to an output stream
					baos.write(this.audioData, 0, nBytesRead);
				}
				//Video loaded. Must destroy the resources (SourceDataLine and AudioInputStreams)
				this.line.drain();
				this.line.stop();
				this.line.close();
				if (this.din != null) {
					this.din.close();
				}
				if (this.in != null) {
					this.in.close();
				}

				//Turn the streamed bytes into a byte array
				this.loadedAudioFileData = baos.toByteArray();

				//An audio file is currently loaded in this instance's byte array - set the flag accordingly
				this.audioFileLoaded = true;
			}
		}
	}

	/**
	 * A method which accepts a {@code java.io.InputStream} to load an entire audio file into memory for enhanced performance during playback. The audio byte array is not returned, but stored internally for playing
	 * @param input The {@code java.io.InputStream} representing the audio file to load into memory
	 * @throws LineUnavailableException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 * @apinote This API should not be used for large media files, seeing as this method attempts to load the whole file into memory. This can result in significant performance degradation along with possible {@code OutOfMemoryErrors} for larger audio files.
	 */
	//Method to load an audio file in memory in the form of a byte array	
	public void loadAudioFile(InputStream input) throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		if (this.audioFileLoaded) {
			throw new IllegalStateException("Cannot load an audio file into memory when one is already loaded. Must clear it first");
		} else {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int nBytesRead;
			AudioFormat decodedFormat = initialisePlayer(input);
			//Preserve this format for playing the file
			this.loadedAudioFileDataFormat = decodedFormat;
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
			this.line = (SourceDataLine) AudioSystem.getLine(info);

			if (this.line != null) {
				this.line.open(decodedFormat);
				//Used as a buffer to write to the byte array
				this.audioData = new byte[decodedFormat.getFrameSize() * Math.round(decodedFormat.getSampleRate() / 10)];
				// Start
				this.line.start();

				//audio streaming from audioData buffer
				while ((nBytesRead = this.din.read(this.audioData, 0, this.audioData.length)) != -1) {
					//Write the current state of the audio byte array to the SourceDataLine, which has similar properties to an output stream
					baos.write(this.audioData, 0, nBytesRead);
				}
				//Video loaded. Must destroy the resources (SourceDataLine and AudioInputStreams)
				this.line.drain();
				this.line.stop();
				this.line.close();
				if (this.din != null) {
					this.din.close();
				}
				if (this.in != null) {
					this.in.close();
				}

				//Turn the streamed bytes into a byte array
				this.loadedAudioFileData = baos.toByteArray();

				//An audio file is currently loaded in this instance's byte array - set the flag accordingly
				this.audioFileLoaded = true;
			}
		}
	}

	public boolean clearAudioFile() {
		if (this.audioFileLoaded) {
			this.loadedAudioFileData = null;
			this.loadedAudioFileDataFormat = null;
			this.audioFileLoaded = false;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * A method to play audio loaded in memory. If the player is active or there is no audio file loaded in memory, an {@code IllegalStateException} will be thrown
	 * @param startPosition The position of the audio file (in microseconds) at which to start playing
	 * @param endPosition The position of the audio file (in microseconds) at which to finish playing
	 * @param controlRunnable A {@code Runnable} to be executed (on the same thread) after audio initialisation yet prior to starting the audio. Useful for reading and modifying audio controls such as gain and reverb.
	 * @throws LineUnavailableException
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	//DO NOT synchronise the start method - any other methods (namely the EDT, among others) which try to use/get a monitor on the object WILL be hung for the duration of the start method
	public void start(long startPosition, long endPosition, Runnable controlRunnable) throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		if (!this.active && this.audioFileLoaded) {
			int nBytesRead = 0, nBytesReadAccumulator;
			synchronized(this) {
				this.active = true;
				this.playbackType = MEMORY_PLAYBACK;
			}
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, this.loadedAudioFileDataFormat);
			this.line = (SourceDataLine) AudioSystem.getLine(info);

			if (line != null) {
				this.line.open(this.loadedAudioFileDataFormat);

				//Get custom start position - frame size (size of all samples) multiplied by frame rate to get length of 1 second of audio byte array. Multiply that by a time (microseconds) and divide the result (NOT the time alone) by 1*10^6
				this.din = new AudioInputStream(new ByteArrayInputStream(this.loadedAudioFileData, (int) ((startPosition * loadedAudioFileDataFormat.getFrameSize() * loadedAudioFileDataFormat.getFrameRate())/1000000), this.loadedAudioFileData.length), this.loadedAudioFileDataFormat, this.loadedAudioFileData.length);
				if (controlRunnable != null) {
					controlRunnable.run();
				}
				line.start();
				do {
					nBytesReadAccumulator = 0;
					while ((nBytesRead = din.read(this.audioData, 0, this.audioData.length)) != -1) {
						nBytesReadAccumulator += nBytesRead;
						if (this.paused) {
							synchronized(this.lockObj) {
								try {
									this.lockObj.wait();
								} catch (InterruptedException ie) {
									ie.printStackTrace();
								}
							}
						} else if (!this.active || nBytesReadAccumulator / (loadedAudioFileDataFormat.getFrameSize() * loadedAudioFileDataFormat.getFrameRate()) >= (endPosition - startPosition)/1000000) {
							//Stop method invoked during playback
							this.loop = false;
							break;
						}
						//Write the current state of the audio byte array to the SourceDataLine, which has similar properties to an output stream
						line.write(this.audioData, 0, nBytesRead);
					}
				} while (this.loop);
			}
			this.stop();
			this.line.drain();
			this.line.stop();
			this.line.close();
			if (this.din != null) {
				this.din.close();
			}
		} else if (this.active) {
			throw new IllegalStateException("Cannot start active player");
		} else if (!this.audioFileLoaded) {
			throw new IllegalStateException("Cannot start player because there is no audio file loaded in memory");
		}
	}

	/*public void start(InputStream istream, long startPosition, long endPosition, Runnable controlRunnable) throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		if (!this.active) {
			synchronized(this) {
				this.active = true;
				this.playbackType = MEMORY_PLAYBACK;
				AudioFormat audioFormat = initialisePlayer(istream);
				this.clip = AudioSystem.getClip();
			}
			this.clip.open(this.din);
			if (controlRunnable != null) {
				controlRunnable.run();
			}
			do {
				this.clip.setMicrosecondPosition(startPosition);
				this.clip.start();
				//Wait until audio clip finishes/reaches end
				try {
					do {
						if (this.paused) {
							synchronized(this.lockObj) {
								this.lockObj.wait();
							}
						} else if (this.clip.getMicrosecondPosition() >= endPosition) {
							this.clip.stop();
							//Not really needed but better safe than sorry...
							break;
						}
						Thread.sleep(10);
						//When a clip is "paused", it is actually stopped after its playback position is obtained. On resuming, the clip is recreated at the playback position is set to that obtained before pausing
						//Might be paused past the block-if-paused synchronisation check and before the loop condition check, rendering the clip inactive, causing the program to think that the clip has finished, when it is actually paused
					} while (this.clip.isActive() || this.paused);
				} catch (InterruptedException ie) {
					this.loop = false;
					ie.printStackTrace();
				}
			} while (this.loop);
			this.stop();
		} else {
			throw new IllegalStateException("Cannot start active player");
		}
	}

	public void start(Clip clip, long startPosition, long endPosition, Runnable controlRunnable) throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		if (!this.active) {
			synchronized(this) {
				this.active = true;
				this.clip = clip;
				this.playbackType = PREDEFINED_CLIP_MEMORY_PLAYBACK;
			}
			if (controlRunnable != null) {
				controlRunnable.run();
			}
			do {
				this.clip.setMicrosecondPosition(startPosition);
				this.clip.start();
				//Wait until audio clip finishes/reaches end
				try {
					do {
						if (this.paused) {
							synchronized(this.lockObj) {
								this.lockObj.wait();
							}
						} else if (this.clip.getMicrosecondPosition() >= endPosition) {
							this.clip.stop();
							//Not really needed but better safe than sorry...
							break;
						}
						Thread.sleep(10);
						//When a clip is "paused", it is actually stopped after its playback position is obtained. On resuming, the clip is recreated at the playback position is set to that obtained before pausing
						//Might be paused past the pause synchronisation check and before the loop condition check, rendering the clip inactive, causing the program to think that the clip has finished, when it is actually paused
					} while (this.clip.isActive() || this.paused);
				} catch (InterruptedException ie) {
					this.loop = false;
					ie.printStackTrace();
				}
			} while (this.loop);
			this.stop();
		} else {
			throw new IllegalStateException("Cannot start active player");
		}
	}*/

	public void pause() {
		if (!this.active) {
			throw new IllegalStateException("Cannot pause an inactive player");
		} else if (this.paused) {
			throw new IllegalStateException("Cannot pause a paused player");
		} else {
			this.paused = true;
			/*if (this.playbackType == MEMORY_PLAYBACK || this.playbackType == PREDEFINED_CLIP_MEMORY_PLAYBACK) {
				this.playbackPausePosition = this.clip.getMicrosecondPosition();
				this.clip.stop();
			}*/
		}
	}
	public void resume() {
		if (!this.active) {
			throw new IllegalStateException("Cannot resume an inactive player");
		} else if (!this.paused) {
			throw new IllegalStateException("Cannot resume a playing player");
		} else {
			this.paused = false;
			//lockObj is used in all playback modes: MEMORY_PLAYBACK, PREDEFINED_CLIP_MEMORY_PLAYBACK and STREAM_PLAYBACK modes
			synchronized(this.lockObj) {
				this.lockObj.notifyAll();
			}
			/*if (this.playbackType == MEMORY_PLAYBACK) {
				this.clip.setMicrosecondPosition(this.playbackPausePosition);
				this.clip.start();
			}*/

		}
	}
	public synchronized void stop() throws IOException {
		//DO NOT destroy any critical player state in the stop() method. The player must be reusable
		if (!this.active) {
			throw new IllegalStateException("Cannot stop an inactive player");
		} else {
			this.active = false;
			this.paused = false;
			//lockObj is used in all playback modes: MEMORY_PLAYBACK, PREDEFINED_CLIP_MEMORY_PLAYBACK and STREAM_PLAYBACK modes - stop this clip even if it is paused
			synchronized(this.lockObj) {
				this.lockObj.notifyAll();
			}
			this.playbackType = NONE;
		}
	}
}

class AudioFormatStreamGroup {
	AudioInputStream ais;
	AudioFormat format;
	public AudioFormatStreamGroup(AudioInputStream ais, AudioFormat format) {
		this.ais = ais;
		this.format = format;
	}
}