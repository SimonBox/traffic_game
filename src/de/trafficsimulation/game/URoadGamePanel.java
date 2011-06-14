package de.trafficsimulation.game;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

public class URoadGamePanel extends URoadGameBase {
  private static final long serialVersionUID = 1L;

  /**
   * Update the sim flow out meter this often.
   */
  private static final int SCORE_TIMER_DELAY_MS = 100;
  
  private final static String CARD_HIGH_FLOW = "high";
  
  private final static String CARD_MEDIUM_FLOW = "medium";
  
  private final static String CARD_OPTIMAL_FLOW = "optimal";
  
  private final static String CARD_LOW_FLOW = "low";
  
  private final URoadCanvas simCanvas;

  private final Timer scoreTimer; 
  
  private BackgroundWarmupRunner warmupPool;
  
  /**
   * Map from flow levels to messages.
   */
  private final SortedMap<Integer, String> breaks =
    new TreeMap<Integer, String>() {
    private static final long serialVersionUID = 1L;
    {
      put(0, CARD_LOW_FLOW);
      put(1800, CARD_OPTIMAL_FLOW);
      put(1880, CARD_MEDIUM_FLOW);
      put(2100, CARD_HIGH_FLOW);
    }
  };
  
  public URoadGamePanel() {
    MessageBubble highFlowMessageBubble = new MessageBubble();
    highFlowMessageBubble.add(Resource.makeStyledTextPane(
        "The junction is congested!\n" +
        "Drag the slider to reduce the traffic on the main road."),
        BorderLayout.CENTER);
    messageContainer.add(highFlowMessageBubble, CARD_HIGH_FLOW);
    
    MessageBubble mediumFlowMessageBubble = new MessageBubble();
    mediumFlowMessageBubble.add(Resource.makeStyledTextPane(
        "The flow is unstable!\n" +
        "It may run OK for a while, but flow will eventually break down.\n" +
        "Drag the slider to reduce the traffic on the main road."),
        BorderLayout.CENTER);
    messageContainer.add(mediumFlowMessageBubble, CARD_MEDIUM_FLOW);
    
    MessageBubble optimalFlowMessageBubble = new MessageBubble();
    optimalFlowMessageBubble.add(Resource.makeStyledTextPane(
        "This flow is pretty close to optimal!"),
        BorderLayout.CENTER);
    optimalFlowMessageBubble.add(new RoundedButton("Go to Level 3!") {
      private static final long serialVersionUID = 1L;
      @Override
      public void click() {
        nextLevel();
      }
    }, BorderLayout.SOUTH);
    messageContainer.add(optimalFlowMessageBubble, CARD_OPTIMAL_FLOW);
    
    MessageBubble lowFlowMessageBubble = new MessageBubble();
    lowFlowMessageBubble.add(Resource.makeStyledTextPane(
        "The flow is low!\n" +
        "Drag the slider to increase the traffic on the main road."),
        BorderLayout.CENTER);
    messageContainer.add(lowFlowMessageBubble, CARD_LOW_FLOW);
    
    //
    // the sim
    // 
    simCanvas = new URoadCanvas();
    simCanvas.setBorder(BorderFactory.createCompoundBorder(BorderFactory
        .createEmptyBorder(PAD, PAD, PAD, PAD), new RoundedBorder(
        Resource.BACKGROUND.brighter(), null, MessageBubble.CORNER_RADIUS,
        MessageBubble.BORDER_WIDTH, true)));
    simCanvas.setTimeStepsPerFrame(TIME_STEPS_PER_FRAME);
    JLabel flowLabel = new JLabel();
    flowLabel.setFont(Resource.HEADER_FONT);
    simCanvas.getFloatPanel().add(flowLabel);
    gameContainer.add(simCanvas, CARD_GAME);
    
    //
    // background thread pool for sim warmups; we don't want this to be null,
    // so we create an empty one to start with
    //
    warmupPool = new BackgroundWarmupRunner();
    
    //
    // periodically update the score
    //
    scoreTimer = new Timer(SCORE_TIMER_DELAY_MS, new ActionListener() {      
      @Override
      public void actionPerformed(ActionEvent e) {
        JLabel label = (JLabel) simCanvas.getFloatPanel().getComponent(0);
        URoadSim sim = simCanvas.getSim();
        if (sim != null) {
          label.setText(String.format("%.0f cars / hour out",
              simCanvas.getSim().getMeanFlowOut() * 3600));
        }
      }
    });
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
    final int flowIn = flowInSlider.getValue();
    ArrayList<SimBase> sims = new ArrayList<SimBase>(1);
    sims.add(simCanvas.getSim());
    warmupPool = new BackgroundWarmupRunner(sims, WARMUP_SECONDS,
        new Runnable() {
      @Override
      public void run() {
        gameCards.show(gameContainer, CARD_GAME);
        showFlowMessage(flowIn);
        simCanvas.resume();
        scoreTimer.start();
      }
    });
  }
  
  private void showFlowMessage(int flowIn) {
    // note: this loop gives us the keys in ascending order
    int flowInKey = FLOW_IN_MIN;
    for (int key : breaks.keySet()) {
      if (flowIn >= key)
        flowInKey = key;
    }
    messageCards.show(messageContainer, breaks.get(flowInKey));
  }

  /**
   * Copy parameters from GUI to given sim.
   * 
   * @param sim not null
   */
  private void setSimParameters(URoadSim sim) {
    sim.qIn = flowInSlider.getValue() / 3600.;
  }
  
  public void stop() {
    scoreTimer.stop();
    simCanvas.stop();
    warmupPool.stop();
  }
  
  public void nextLevel() {
    
  }
  
  /** For testing */
  public static void main(String [] args) {
    JFrame f = new JFrame("ring test");
    f.setSize(1024,600);
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    URoadGamePanel p = new URoadGamePanel() {
      private static final long serialVersionUID = 1L;
    };
    f.add(p);
    f.setVisible(true);
    p.start();
  } 
}
