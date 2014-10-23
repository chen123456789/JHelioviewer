package org.helioviewer.jhv.gui.states;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.helioviewer.jhv.opengl.GLInfo;

/**
 * Singleton that controls the current state, i.e. 2D or 3D.
 * {@link StateChangeListener}s can be added for notifications about the current
 * state. By default, the 3D state is enabled.
 * 
 * @author Simon Sp�rri (simon.spoerri@fhnw.ch)
 * 
 */
public class StateController {
    private static StateController instance = new StateController();

    private CopyOnWriteArrayList<StateChangeListener> stateChangeListeners = new CopyOnWriteArrayList<StateChangeListener>();

    private GuiState3DWCS currentState;
    
    
    private StateController() {
        set3DState();
        
    }

    public static StateController getInstance() {
        return StateController.instance;
    }

    public void set3DState() {
        this.setState(ViewStateEnum.View3D.getState());
    }

    private void setState(GuiState3DWCS newState) {
        State oldState = this.currentState;
        if (newState != oldState) {
            this.currentState = newState;
            fireStateChange(newState, oldState);
        }
    }

    public GuiState3DWCS getCurrentState() {
        return this.currentState;
    }
    
    public State getState(){
    	return ViewStateEnum.View3D.getState();
    }

    public void addStateChangeListener(StateChangeListener listener) {
            this.stateChangeListeners.add(listener);
    }

    public void removeStateChangeListener(StateChangeListener listener) {
        this.stateChangeListeners.remove(listener);
    }

    protected void fireStateChange(State newState, State oldState) {
            for (StateChangeListener listener : this.stateChangeListeners) {
                listener.stateChanged(newState, oldState, this);
            }
    }

    public static interface StateChangeListener {
        public void stateChanged(State newState, State oldState, StateController stateController);
    }
}
