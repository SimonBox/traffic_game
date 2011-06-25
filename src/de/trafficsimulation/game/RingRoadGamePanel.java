package de.trafficsimulation.game;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

import de.trafficsimulation.core.Constants;

public abstract class RingRoadGamePanel extends JPanel implements Constants {

  /**
   * Update the adaptive messages at this interval, in milliseconds.
   */
  private static final int ADAPTIVE_MESSAGE_TIMER_DELAY_MS = 100;

  private final static String FREE_FLOW_CARD = "free";
  private final static String CONGESTION_CARD = "congestion";
  private final static String JAM_CARD = "jam";

  /**
   * The density for the 'hint' to encourage the user to increase the density.
   */
  private final static int HINT_DENSITY_INVKM = 60;

  /**
   * The density of the road when the sim starts.
   */
  private final static int INITIAL_DENSITY_INVKM = 20;

  /**
   * The message cards to show, according to the MIN_SPEED_THRESHOLDS.
   */
  private static final String[] MIN_SPEED_CARDS = { JAM_CARD, CONGESTION_CARD,
      FREE_FLOW_CARD };

  /**
   * Minimum speed thresholds used to set the adaptive messages; speeds are in
   * meters per second.
   */
  private static final double[] MIN_SPEED_THRESHOLDS = { 2.0, 8.0 };
  /**
   * Don't change the current state unless the minimum speed is at least this
   * much outside of the interval for the current state. This avoids rapid
   * switching of messages due to noise.
   */
  private static final double MIN_SPEED_TOLERANCE = 1.0;
  private static final long serialVersionUID = 1L;

  /** For testing */
  public static void main(String[] args) {
    JFrame f = new JFrame("ring test");
    f.setSize(800, 600);
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    RingRoadGamePanel p = new RingRoadGamePanel() {
      private static final long serialVersionUID = 1L;

      @Override
      public void goToNextLevel() {
      }
    };
    f.add(p);
    f.setVisible(true);
    p.start();
  }

  private final BigSlider densitySlider;

  private final JPanel messageContainer;

  private final CardLayout messageLayout;

  private final ThresholdMachine messageMachine;

  private final Timer messageTimer;

  private final RingRoadCanvas ringRoadCanvas;

  public RingRoadGamePanel() {
    setLayout(new BorderLayout());
    
    //
    // title bar
    //
    GameChoicePanel titleBar = new GameChoicePanel(false, "phantom jams", true) {
      private static final long serialVersionUID = 1L;

      @Override
      public void onNextClicked() {
        goToNextLevel();
      }
    };
    add(titleBar, BorderLayout.NORTH);

    //
    // float UI in the center of the ring road
    //
    ringRoadCanvas = new RingRoadCanvas();
    ringRoadCanvas.setBorder(BorderFactory.createEmptyBorder(UI.PAD, UI.PAD,
        UI.PAD, UI.PAD));
    ringRoadCanvas.setLayout(new GridBagLayout());
    add(ringRoadCanvas, BorderLayout.CENTER);

    //
    // the bubble
    //
    JPanel controlPanel = new MessageBubble();
    controlPanel.setLayout(new BorderLayout());
    ringRoadCanvas.add(controlPanel);

    densitySlider = new BigSlider(DENS_MIN_INVKM, DENS_MAX_INVKM,
        INITIAL_DENSITY_INVKM, HINT_DENSITY_INVKM) {
      private static final long serialVersionUID = 1L;
      {
        setBorder(BorderFactory.createEmptyBorder(0, 0, UI.PAD, 0));
      }

      @Override
      public void onValueUpdated() {
        updateDensity();
      }
    };
    controlPanel.add(densitySlider, BorderLayout.NORTH);

    //
    // message panels
    //
    messageContainer = new JPanel();
    controlPanel.add(messageContainer, BorderLayout.CENTER);
    messageLayout = new CardLayout();
    messageContainer.setLayout(messageLayout);

    JPanel freeFlowMessage = new JPanel(new BorderLayout());
    freeFlowMessage.add(UI
        .makeStyledTextPane("Traffic Report: traffic is flowing freely.\n"
            + "Try dragging the slider to the right to add more cars..."),
        BorderLayout.CENTER);
    messageContainer.add(freeFlowMessage, FREE_FLOW_CARD);

    JPanel congestionMessage = new JPanel(new BorderLayout());
    congestionMessage.add(UI.makeStyledTextPane("Traffic Report: congestion!\n"
        + "Try dragging the slider to the right to add even more cars..."),
        BorderLayout.CENTER);
    messageContainer.add(congestionMessage, CONGESTION_CARD);

    JPanel jamMessage = new JPanel(new BorderLayout());
    jamMessage.add(UI.makeStyledTextPane("Traffic Report: phantom jams!"),
        BorderLayout.CENTER);
    messageContainer.add(jamMessage, JAM_CARD);

    messageMachine = new ThresholdMachine(MIN_SPEED_THRESHOLDS,
        MIN_SPEED_CARDS, MIN_SPEED_TOLERANCE);

    messageTimer = new Timer(ADAPTIVE_MESSAGE_TIMER_DELAY_MS,
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updateMessage();
          }
        });
  }

  /**
   * Called when the user presses the next button.
   */
  public abstract void goToNextLevel();

  public void start() {
    ringRoadCanvas.start(42);
    densitySlider.setValue(INITIAL_DENSITY_INVKM);
    updateDensity();
    messageTimer.start();
    updateMessage();
  }

  public void stop() {
    ringRoadCanvas.stop();
    messageTimer.stop();
  }

  private void updateDensity() {
    double density = densitySlider.getValue();
    ringRoadCanvas.getSim().setDensity(density * 1e-3);
  }

  private void updateMessage() {
    RingRoadSim sim = ringRoadCanvas.getSim();
    if (sim == null)
      return;

    double minSpeed = sim.getStreet().getMinSpeed();
    if (messageMachine.observe(minSpeed)) {
      messageLayout.show(messageContainer, messageMachine.getState());

      // only show the hint at low density
      if (messageMachine.getState().equals(FREE_FLOW_CARD)) {
        densitySlider.setHintValue(HINT_DENSITY_INVKM);
      } else {
        densitySlider.setHintValue(Double.NaN);
      }
    }
  }
}
