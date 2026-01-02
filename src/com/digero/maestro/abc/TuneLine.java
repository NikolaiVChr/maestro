package com.digero.maestro.abc;

public class TuneLine {
	public int seminoteStep = 0;
//	public boolean remove = false;
	public int dialogLine = -1;
	public int tempo = 0;
	public int fade = 0;
	public int accelerando = 0;

	// inclusive:
	public float startBar = 0;
	public long startTick = -1L;
	
	// exclusive:
	public float endBar = 0;
	public long endTick = -1L;

    public TuneLine() {

    }

    public TuneLine(TuneLine orig) {
        this.startBar = orig.startBar;
        this.startTick = orig.startTick;
        this.endBar = orig.endBar;
        this.endTick = orig.endTick;
        this.tempo = orig.tempo;
        this.fade = orig.fade;
        this.accelerando = orig.accelerando;
        this.dialogLine = orig.dialogLine;
        this.seminoteStep = orig.seminoteStep;
    }

    @Override
	@SuppressWarnings("HardCodedStringLiteral")
	public String toString() {
		return "Tune Line " + startBar + " to " + endBar + ": tempo=" + tempo + " seminoteStep=" + seminoteStep
				 + " fade=" + fade + " accelerando=" + accelerando + " dialogLine=" + dialogLine;
	}
}
