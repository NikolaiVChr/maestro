package com.digero.maestro.abc;

import com.digero.common.abc.LotroInstrument;

public interface NumberedAbcPart {
	LotroInstrument getInstrument();

	void setInstrument(LotroInstrument instrument);

    int getFirstNumber();

	int getPartNumber();

    /**
     * Returns true if the part number was manually assigned.
     * Returns false if the part number was automatically assigned.
     * Return null if from old project and it's not known.
     */
    Boolean isPartNumberManuallyAssigned();

    void setPartNumberManuallyAssigned(boolean manuallyAssigned, boolean notifyListeners);

    void notifyPartNumberManuallyAssigned();

	void setPartNumber(int partNumber);
}
