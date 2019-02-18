package net.sf.mzmine.modules.datapointprocessing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.MZmineProcessingStep;
import net.sf.mzmine.modules.datapointprocessing.DataPointProcessingController.ControllerStatus;

/**
 * There will be a single instance of this class, use getInst(). This class keeps track of every
 * DataPointProcessingController and manages their assignment to the TaskController.
 * 
 * @author SteffenHeu steffen.heuckeroth@gmx.de / s_heuc03@uni-muenster.de
 *
 */
public class DataPointProcessingManager implements Runnable {

  private static final DataPointProcessingManager inst = new DataPointProcessingManager();
  private int MAX_RUNNING = 5;

  private static Logger logger = Logger.getLogger(DataPointProcessingManager.class.getName());

  private boolean enabled;

  private List<DataPointProcessingController> waiting;
  private List<DataPointProcessingController> running;

  private List<MZmineProcessingStep<DataPointProcessingModule>> processingList;

  public DataPointProcessingManager() {
    waiting = new ArrayList<>();
    running = new ArrayList<>();
    
    processingList = new ArrayList<MZmineProcessingStep<DataPointProcessingModule>>();
    enabled = false;
  }

  public static DataPointProcessingManager getInst() {
    return inst;
  }

  /**
   * Adds a controller to the end of the waiting list.
   * 
   * @param controller Controller to add.
   */
  public void addController(DataPointProcessingController controller) {
    synchronized (waiting) {
      if (waiting.contains(controller)) {
        logger.fine("Warning: Controller was already added to waiting list at index "
            + waiting.indexOf(controller) + "/" + waiting.size() + ". Skipping.");
        return;
      }
      waiting.add(controller);
    }
    logger.finest("Controller added to waiting list. (size = " + waiting.size() + ")");
  }

  /**
   * Removes a controller from the waiting list. Use this if the current plot has changed.
   * 
   * @param controller
   */
  public void removeWaitingController(DataPointProcessingController controller) {
    if (!waiting.contains(controller))
      return;

    synchronized (waiting) {
      waiting.remove(controller);
    }
    logger.finest("Controller removed from wating list. (size = " + waiting.size() + ")");
  }

  /**
   * Adds a controller to the running list. Don't use publicly. Is called by startNextController()
   * if running.size < MAX_RUNNING.
   * 
   * @param controller
   */
  private void addRunningController(DataPointProcessingController controller) {
    synchronized (running) {
      if (running.contains(controller)) {
        logger.fine("Warning: Controller was already added to waiting list at index "
            + running.indexOf(controller) + "/" + running.size() + ". Skipping.");
        return;
      }
      running.add(controller);
    }
    logger.finest("Controller added to running list. (size = " + running.size() + ")");
  }

  /**
   * Removes a controller from the running list. Don't use publicly. Is called by the
   * DPControllerStatusListener in the startNextController method, if the task was canceled or
   * finished.
   * 
   * @param controller
   */
  private void removeRunningController(DataPointProcessingController controller) {
    if (!running.contains(controller))
      return;

    synchronized (running) {
      running.remove(controller);
    }
    logger.finest("Controller removed from running list. (size = " + running.size() + ")");
  }

  /**
   * Clears the list of all waiting controllers.
   */
  private void removeAllWaitingControllers() {
    synchronized (waiting) {
      waiting.clear();
    }
  }

  /**
   * Tries to start the next controller from the waiting list and adds a listener to automatically
   * start the next one when finished. Every SpectraPlot will call this method after adding its
   * controller. TODO: Maybe just call this in addController?
   */
  public void startNextController() {
    if (!isEnabled())
      return;

    if (running.size() >= MAX_RUNNING) {
      logger.info("Too much controllers running, cannot start the next one.");
      return;
    }
    if (waiting.isEmpty()) {
      logger.info("No more waiting controllers, cannot start the next one.");
      return;
    }

    DataPointProcessingController next;

    synchronized (waiting) {
      next = waiting.get(0);
      removeWaitingController(next);
    }

    addRunningController(next);
    next.addControllerStatusListener(new DPPControllerStatusListener() {

      @Override
      public void statusChanged(DataPointProcessingController controller,
          ControllerStatus newStatus, ControllerStatus oldStatus) {
        if (newStatus == ControllerStatus.FINISHED) {
          // One controller finished, now we can remove it and start the next one.
          removeRunningController(controller);
          startNextController();
          logger.finest("Controller finished, trying to start the next one. + (size = "
              + running.size() + ")");
        } else if (newStatus == ControllerStatus.CANCELED) {
          // this will be called, when the controller is forcefully canceled. The current task will
          // be completed, then the status will be changed and this method is called.
          removeRunningController(controller);
        } else if (newStatus == ControllerStatus.ERROR) {
          // if a controller's task errors out, we should cancel the whole controller here
          // the controller status is set to ERROR automatically, if a task error's out.

          // since the next controller wont be started, just using cancelTasks() here is not
          // sufficient, we have to remove it manually
          removeRunningController(controller);
        }
      }
    });

    next.execute(); // this will start the actual task via the controller method.
    logger.finest("Started controller from running list. (size = " + running.size() + ")");
  }

  @Override
  public void run() {
    while (true) {

      try {
        TimeUnit.MILLISECONDS.sleep(200);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  /**
   * Cancels the execution of a specific controller or removes it from wating list.
   * 
   * @param controller
   */
  public void cancelController(DataPointProcessingController controller) {
    synchronized (waiting) {
      if (waiting.contains(controller)) {
        controller.cancelTasks();
        // removing the controller will be executed by the statusListener in startNextController()
        // since the forcedStatus of the controller has been set. Controller.execute checks before
        // every task launch if the controller was canceled.
        // removeWaitingController(controller);
      }
    }
    synchronized (running) {
      if (running.contains(controller)) {
        controller.cancelTasks();
        // removing the controller will be executed by the statusListener in startNextController()
        // removeRunningController(controller);
      }
    }
  }

  /**
   * Cancels the execution of all running controllers. Keep in mind that calling only this method
   * will only cancel the running ones, the currently waiting ones will be started afterwards.
   * Consider calling removeAllWaiting first or use cancelAndRemoveAll() instead.
   */
  public void cancelAllRunning() {
    synchronized (running) {
      for (DataPointProcessingController c : running) {
        c.cancelTasks();
      }
    }
  }

  /**
   * Cancels every running task and removed all waiting controllers.
   */
  public void cancelAndRemoveAll() {
    removeAllWaitingControllers();
    synchronized (running) {
      for (DataPointProcessingController c : running) {
        c.cancelTasks();
      }
    }
  }

  /**
   * Convenience method to double check if a controller is allowed to run.
   * 
   * @param controller
   * @return true or false if the controller is still in the running list. If this is false and the
   *         controller wants to execute() something went wrong. Check the setStatus method of the
   *         controller and the tasks.
   */
  public boolean isRunning(DataPointProcessingController controller) {
    return running.contains(controller);
  }

  /**
   * Adds a processing step to the list.
   * 
   * @param step Processing step to add.
   * @return {@link Collection#add}
   */
  public boolean addProcessingStep(MZmineProcessingStep<DataPointProcessingModule> step) {
    return processingList.add(step);
  }

  /**
   * Removes a processing step from the list.
   * 
   * @param step Processing step to remove.
   * @return {@link Collection#remove}
   */
  public boolean removeProcessingStep(MZmineProcessingStep<DataPointProcessingModule> step) {
    return processingList.remove(step);
  }

  /**
   * Clears the list of processing steps
   */
  public void clearProcessingSteps() {
    processingList.clear();
  }

  public List<MZmineProcessingStep<DataPointProcessingModule>> getProcessingSteps() {
    return processingList;
  }

  /**
   * Sets the processing list.
   * 
   * @param list New processing list.
   */
  public void setProcessingSteps(List<MZmineProcessingStep<DataPointProcessingModule>> list) {
    processingList = list;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    logger.finest("Enabled changed to " + enabled);
  }
}
