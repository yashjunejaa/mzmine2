/*
 * Copyright 2006 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

/**
 *
 */
package net.sf.mzmine.io.netcdf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import net.sf.mzmine.interfaces.Scan;
import net.sf.mzmine.io.RawDataFile;
import net.sf.mzmine.io.RawDataFile.PreloadLevel;
import net.sf.mzmine.util.Logger;

/**
 *
 */
public class NetCDFFile implements RawDataFile {

    private File originalFile;
    private File currentFile;

    private PreloadLevel preloadLevel;
    private StringBuffer dataDescription;

    private int numOfScans = 0;

    private double dataMinMZ, dataMaxMZ, dataMinRT, dataMaxRT;

    private Hashtable<Integer, Double> dataMaxBasePeakIntensity, dataMaxTIC;

	private NetCDFFileParser cdfParser;

    /**
     * Preloaded scans
     */
    private Hashtable<Integer, NetCDFScan> scans;

    /**
     * Maps scan level -> list of scan numbers in that level
     */
    private Hashtable<Integer, ArrayList<Integer>> scanNumbers;


    /**
     */
    NetCDFFile(File originalFile, File currentFile, PreloadLevel preloadLevel) {
        this.originalFile = originalFile;
        this.currentFile = currentFile;
        this.preloadLevel = preloadLevel;

        dataDescription = new StringBuffer();
        scanNumbers = new Hashtable<Integer, ArrayList<Integer>>();
        dataMaxBasePeakIntensity = new Hashtable<Integer, Double>();
        dataMaxTIC = new Hashtable<Integer, Double>();
        if (preloadLevel != PreloadLevel.NO_PRELOAD) scans = new Hashtable<Integer, NetCDFScan>();
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getOriginalFile()
     */
    public File getOriginalFile() {
        return originalFile;
    }

    public File getCurrentFile() {
		return currentFile;
	}

    /**
     * @see net.sf.mzmine.io.RawDataFile#getNumOfScans()
     */
    public int getNumOfScans() {
        return numOfScans;
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getMSLevels()
     */
    public int[] getMSLevels() {

        Set<Integer> msLevelsSet = scanNumbers.keySet();
        int[] msLevels = new int[msLevelsSet.size()];
        int index = 0;
        Iterator<Integer> iter = msLevelsSet.iterator();
        while (iter.hasNext())
            msLevels[index++] = iter.next().intValue();
        Arrays.sort(msLevels);
        return msLevels;

    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getScanNumbers(int)
     */
    public int[] getScanNumbers(int msLevel) {

        ArrayList<Integer> numbersList = scanNumbers.get(new Integer(msLevel));
        if (numbersList == null)
            return null;

        int[] numbersArray = new int[numbersList.size()];
        int index = 0;
        Iterator<Integer> iter = numbersList.iterator();
        while (iter.hasNext())
            numbersArray[index++] = iter.next().intValue();
        Arrays.sort(numbersArray);
        return numbersArray;
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getScan(int)
     */
    public Scan getScan(int scanNumber) throws IOException {


        /* check if we have desired scan in memory */
        if (scans != null) {
            NetCDFScan preloadedScan = scans.get(new Integer(scanNumber));
            if (preloadedScan != null)
                return preloadedScan;
        }

		// Fetch scan from file
		cdfParser.openFile();
		NetCDFScan fetchedScan = cdfParser.parseScan(scanNumber);
		cdfParser.closeFile();

		return fetchedScan;

    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataDescription()
     */
    public String getDataDescription() {
        return dataDescription.toString();
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataMinMZ()
     */
    public double getDataMinMZ() {
        return dataMinMZ;
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataMaxMZ()
     */
    public double getDataMaxMZ() {
        return dataMaxMZ;
    }

    public String toString() {
        return originalFile.getName();
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataMinRT()
     */
    public double getDataMinRT() {
        return dataMinRT;
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataMaxRT()
     */
    public double getDataMaxRT() {
        return dataMaxRT;
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataMaxBasePeakIntensity(int)
     */
    public double getDataMaxBasePeakIntensity(int msLevel) {
        return dataMaxBasePeakIntensity.get(msLevel).doubleValue();
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getDataMaxTotalIonCurrent(int)
     */
    public double getDataMaxTotalIonCurrent(int msLevel) {
        return dataMaxTIC.get(msLevel).doubleValue();
    }

    /**
     * @see net.sf.mzmine.io.RawDataFile#getPreloadLevel()
     */
    public PreloadLevel getPreloadLevel() {
        return preloadLevel;
    }


	void addDataDescription(String description) {
        if (dataDescription.length() > 0)
            dataDescription.append("\n");
        dataDescription.append(description);
    }

	void addParser(NetCDFFileParser cdfParser) {
		this.cdfParser = cdfParser;
	}


    /**
     *
     */
    void addScan(NetCDFScan newScan) {

        /* if we want to keep data in memory, save a reference */
        if (preloadLevel == PreloadLevel.PRELOAD_ALL_SCANS)
            scans.put(new Integer(newScan.getScanNumber()), newScan);

        if ((numOfScans == 0) || (dataMinMZ > newScan.getMZRangeMin()))
            dataMinMZ = newScan.getMZRangeMin();
        if ((numOfScans == 0) || (dataMaxMZ < newScan.getMZRangeMax()))
            dataMaxMZ = newScan.getMZRangeMax();
        if ((numOfScans == 0) || (dataMinRT > newScan.getRetentionTime()))
            dataMinRT = newScan.getRetentionTime();
        if ((numOfScans == 0) || (dataMaxRT < newScan.getRetentionTime()))
            dataMaxRT = newScan.getRetentionTime();
        if ((dataMaxBasePeakIntensity.get(newScan.getMSLevel()) == null)
                || (dataMaxBasePeakIntensity.get(newScan.getMSLevel()) < newScan
                        .getBasePeakIntensity()))
            dataMaxBasePeakIntensity.put(newScan.getMSLevel(), newScan
                    .getBasePeakIntensity());

        double scanTIC = 0;

        for (double intensity : newScan.getIntensityValues())
            scanTIC += intensity;

        if ((dataMaxTIC.get(newScan.getMSLevel()) == null)
                || (scanTIC > dataMaxTIC.get(newScan.getMSLevel())))
            dataMaxTIC.put(newScan.getMSLevel(), scanTIC);

        ArrayList<Integer> scanList = scanNumbers.get(new Integer(newScan
                .getMSLevel()));
        if (scanList == null) {
            scanList = new ArrayList<Integer>(64);
            scanNumbers.put(new Integer(newScan.getMSLevel()), scanList);
        }
        scanList.add(new Integer(newScan.getScanNumber()));

        numOfScans++;

    }



}
