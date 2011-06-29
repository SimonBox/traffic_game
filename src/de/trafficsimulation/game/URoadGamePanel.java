package de.trafficsimulation.game;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * 
 * Flow states: breakdown, congestion, free - we may not see congestion -- it
 * usually either breaks down or it doesn't - measuring inside lane flow --
 * smooth enough?
 * 
 * Flow in: thresholds as before (low, opt, unstable, high)
 * 
 * Flow out: - issue with EMA delay (too slow)
 * 
 * Feedback states: 1. Flow is free but low. 2. Flow is free and near-optimal.
 * 3. Flow is too high but not broken down (unstable). 4. Flow has broken down.
 * 
 * Thresholds required (three regions: LOW, OPT, HIGH): 1. LOW_FLOW threshold (<
 * 1700) 2. OPT_FLOW threshold (< 1800) (or maybe up to 1850)
 * 
 * LOW/FREE -- too low OPT/FREE -- optimal, OK HIGH/FREE -- unstable any/CONG --
 * flow broke down; try reducing the flow
 * 
 */
public class URoadGamePanel extends URoadGameBase {
  private static final long serialVersionUID = 1L;

  private final static String FREE_STATE = "free";

  private final static String CONGESTION_STATE = "congestion";

  private final static String FREE_LOW_CARD = "low";

  private final static String FREE_OPTIMAL_CARD = "optimal";

  private final static String FREE_HIGH_CARD = "high";

  private final static String CONGESTION_CARD = "congestion";
  
  private static final int TARGET_FPS = 100;

  /**
   * Update the adaptive messages at this interval, in milliseconds.
   */
  private static final int ADAPTIVE_MESSAGE_TIMER_DELAY_MS = 100;

  /**
   * Minimum speed thresholds used to set the adaptive messages; speeds are in
   * meters per second.
   */
  private static final double[] MIN_SPEED_THRESHOLDS = { 1.5 };

  /**
   * The states that correspond to the MIN_SPEED_THRESHOLDS.
   */
  private static final String[] MIN_SPEED_STATES = { FREE_STATE,
      CONGESTION_STATE };

  /**
   * Don't change the current state unless the minimum speed is at least this
   * much outside of the interval for the current state. This avoids rapid
   * switching of messages due to noise.
   */
  private static final double MIN_SPEED_TOLERANCE = 1.0;
  
  /**
   * If the target in flow is less than this, in cars per hour, and there is
   * free flow, we display the low flow message.
   */
  private static final double LOW_FLOW_THRESHOLD = 1700;
  
  /**
   * If the target in flow is less than this, in cars per hour, but larger
   * than LOW_FLOW_THRESHOLD, and there is free flow, we display the optimal
   * flow message; if the flow is over this threshold and there is free flow,
   * , we show the high flow (unstable) message.
   */
  private static final double OPTIMAL_FLOW_THRESHOLD = 1850;
  
  private final URoadCanvas simCanvas;

  private final ThresholdMachine messageMachine;

  private final Timer messageTimer;

  private BackgroundWarmupRunner warmupPool;

  public URoadGamePanel() {
    super("flow breakdown", false);

    //
    // messages
    //
    JPanel freeLowMessage = new JPanel();
    freeLowMessage.setBackground(Color.WHITE);
    freeLowMessage.add(UI.makeStyledTextPane("Traffic Report: free flow\n"
        + "The junction is operating well below capacity.\n"
        + "Drag the slider to increase the target flow on the main road."),
        BorderLayout.CENTER);
    messageContainer.add(freeLowMessage, FREE_LOW_CARD);

    JPanel freeOptimalMessage = new JPanel();
    freeOptimalMessage.setBackground(Color.WHITE);
    freeOptimalMessage
        .add(
            UI.makeStyledTextPane("Traffic Report: free flow\n"
                + "The junction is running near capacity, and traffic is flowing freely.\n"
                + "Well done!\n"), BorderLayout.CENTER);
    messageContainer.add(freeOptimalMessage, FREE_OPTIMAL_CARD);

    JPanel freeHighMessage = new JPanel();
    freeHighMessage.setBackground(Color.WHITE);
    freeHighMessage.add(UI
        .makeStyledTextPane("Traffic Report: free flow, for now...\n"
            + "The junction is over-capacity. Traffic is flowing freely now,\n"
            + "but flow may break down soon.\n"
            + "Drag the slider to decrease the target flow on the main road."),
        BorderLayout.CENTER);
    messageContainer.add(freeHighMessage, FREE_HIGH_CARD);

    JPanel congestionMessage = new JPanel();
    congestionMessage.setBackground(Color.WHITE);
    congestionMessage.add(UI.makeStyledTextPane("Traffic Report: congestion\n"
        + "Drag the slider to decrease the target flow on the main road."),
        BorderLayout.CENTER);
    messageContainer.add(congestionMessage, CONGESTION_CARD);

    //
    // message state machine and timer
    //

    messageMachine = new ThresholdMachine(MIN_SPEED_THRESHOLDS,
        MIN_SPEED_STATES, MIN_SPEED_TOLERANCE);

    messageTimer = new Timer(ADAPTIVE_MESSAGE_TIMER_DELAY_MS,
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updateMessage();
          }
        });

    //
    // the sim
    //
    simCanvas = new URoadCanvas(TARGET_FPS);
    simCanvas.setBorder(BorderFactory.createCompoundBorder(BorderFactory
        .createEmptyBorder(PAD, PAD, PAD, PAD),
        new RoundedBorder(UI.BACKGROUND.brighter(), null,
            MessageBubble.CORNER_RADIUS, MessageBubble.BORDER_WIDTH, true)));
    simCanvas.setTimeStepsPerFrame(TIME_STEPS_PER_FRAME);
    JLabel flowLabel = new JLabel();
    flowLabel.setFont(UI.HEADER_FONT);
    simCanvas.getFloatPanel().add(flowLabel);
    gameContainer.add(simCanvas, CARD_GAME);

    //
    // background thread pool for sim warmups; we don't want this to be null,
    // so we create an empty one to start with
    //
    warmupPool = new BackgroundWarmupRunner();
  }

  protected void updateMessage() {
    if (simCanvas.getSim() == null)
      return;
    
    double minSpeed = simCanvas.getSim().getMinSpeedInInsideLane();
    messageMachine.observe(minSpeed);
    if (messageMachine.getState().equals(FREE_STATE)) {
      messageCards.show(messageContainer, CONGESTION_CARD);
    } else {
      double flowIn = flowInSlider.getValue();
      if (flowIn < LOW_FLOW_THRESHOLD) {
        messageCards.show(messageContainer, FREE_LOW_CARD);
      } else if (flowIn < OPTIMAL_FLOW_THRESHOLD) {
        messageCards.show(messageContainer, FREE_OPTIMAL_CARD);
      } else {
        messageCards.show(messageContainer, FREE_HIGH_CARD);
      }
    }
  }

  @Override
  protected void updateFlowIn() {
    // stop previous run, if any
    stop();

    // will have to run some warmups
    messageCards.show(messageContainer, CARD_WARMUP);
    gameCards.show(gameContainer, CARD_WARMUP);

    // create new sim, but don't start running yet
    simCanvas.start();
    setSimParameters(simCanvas.getSim());
    simCanvas.pause();

    // run warmup in the background
    ArrayList<SimBase> sims = new ArrayList<SimBase>(1);
    sims.add(simCanvas.getSim());
    warmupPool = new BackgroundWarmupRunner(sims, WARMUP_SECONDS,
        new Runnable() {
          @Override
          public void run() {
            gameCards.show(gameContainer, CARD_GAME);
            messageTimer.start();
            simCanvas.resume();
          }
        });
  }

  /**
   * Copy parameters from GUI to given sim.
   * 
   * @param sim
   *          not null
   */
  private void setSimParameters(URoadSim sim) {
    sim.qIn = flowInSlider.getValue() / 3600.;
  }

  public void stop() {
    messageTimer.stop();
    simCanvas.stop();
    warmupPool.stop();
  }

  /** For testing */
  public static void main(String[] args) {
    JFrame f = new JFrame("ring test");
    f.setSize(1024, 600);
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    URoadGamePanel p = new URoadGamePanel() {
      private static final long serialVersionUID = 1L;
    };
    f.add(p);
    f.setVisible(true);
    p.start();
  }

  @Override
  protected void onBackClicked() {
    // nop
  }

  @Override
  protected void onNextClicked() {
    // nop
  }
}
