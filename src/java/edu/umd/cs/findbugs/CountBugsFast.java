/*
 * FindBugs - Find Bugs in Java programs
 * Copyright (C) 2005, University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import edu.umd.cs.findbugs.config.CommandLine;

/**
 * Count bugs matching given criteria using SAX events.
 * Avoids building the whole BugCollection in memory.
 * 
 * @author David Hovemeyer
 */
public class CountBugsFast {
	
	private InputStream inputStream;
	private Set<String> categorySet;
	private Set<String> abbrevSet;
	private int minPriority;
	private int count;
	
	public CountBugsFast(InputStream in) {
		this.inputStream = in;
		this.categorySet = new HashSet<String>();
		this.abbrevSet = new HashSet<String>();
		this.minPriority = Detector.NORMAL_PRIORITY;
	}
	
	/**
	 * @param minPriority The minPriority to set.
	 */
	public void setMinPriority(int minPriority) {
		this.minPriority = minPriority;
	}
	
	public void setCategories(String categories) {
		buildSetFromString(categories, categorySet);
	}
	
	public void setAbbrevs(String abbrevs) {
		buildSetFromString(abbrevs, abbrevSet);
	}

	private void buildSetFromString(String str, Set<String> set) {
		StringTokenizer t = new StringTokenizer(str, ",");
		while (t.hasMoreTokens()) {
			String category = t.nextToken();
			set.add(category);
		}
	}
	
	public CountBugsFast execute() throws IOException, SAXException {
		DefaultHandler handler = new DefaultHandler() {
			/* (non-Javadoc)
			 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
			 */
			//@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				if (qName.equals("BugInstance")) {
					String type = attributes.getValue("type");
					String priority = attributes.getValue("priority");
					
					if (type == null || priority == null)
						return;
					
					BugPattern pattern = I18N.instance().lookupBugPattern(type);
					
					if (pattern == null &&
							(!categorySet.isEmpty() || !abbrevSet.isEmpty()))
						return;
					
					if (!categorySet.isEmpty() && !categorySet.contains(pattern.getCategory()))
						return;
					
					if (!abbrevSet.isEmpty() && !abbrevSet.contains(pattern.getAbbrev()))
						return;
					
					if (Integer.parseInt(priority) > minPriority)
						return;
					
					++count;
				}
			}
		};
		
		// FIXME: for now, use dom4j's XML parser
		XMLReader xr = new org.dom4j.io.aelfred.SAXDriver();

		xr.setContentHandler(handler);
		xr.setErrorHandler(handler);
		
		InputSource inputSource = new InputSource(inputStream);
		xr.parse(inputSource);
		
		return this;
	}
	
	/**
	 * @return Returns the count.
	 */
	public int getCount() {
		return count;
	}
	
	static class CountBugsFastCommandLine extends CommandLine {
		int minPriority = Detector.NORMAL_PRIORITY;
		String categories;
		String abbrevs;
		
		CountBugsFastCommandLine() {
			addOption("-categories", "cat1,cat2...", "set bug categories");
			addOption("-abbrevs", "abbrev1,abbrev2...", "set bug type abbreviations");
			addOption("-minPriority", "priority", "set min bug priority (3=low, 2=medium, 1=high)");
		}
		
		/**
		 * @return Returns the categories.
		 */
		public String getCategories() {
			return categories;
		}
		
		/**
		 * @return Returns the abbrevs.
		 */
		public String getAbbrevs() {
			return abbrevs;
		}
		
		/**
		 * @return Returns the minPriority.
		 */
		public int getMinPriority() {
			return minPriority;
		}
		
		/* (non-Javadoc)
		 * @see edu.umd.cs.findbugs.config.CommandLine#handleOption(java.lang.String, java.lang.String)
		 */
		//@Override
		protected void handleOption(String option, String optionExtraPart) throws IOException {
			throw new IllegalArgumentException("Unknown option: " + option);
		}
		
		/* (non-Javadoc)
		 * @see edu.umd.cs.findbugs.config.CommandLine#handleOptionWithArgument(java.lang.String, java.lang.String)
		 */
		//@Override
		protected void handleOptionWithArgument(String option, String argument) throws IOException {
			if (option.equals("-categories")) {
				categories = argument;
			} else if (option.equals("-abbrevs")) {
				abbrevs = argument;
			} else if (option.equals("-minPriority")) {
				minPriority = Integer.parseInt(argument);
			} else {
				throw new IllegalArgumentException("Unknown option: " + option);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		DetectorFactoryCollection.instance(); // load plugins
		
		CountBugsFastCommandLine commandLine = new CountBugsFastCommandLine();
		int argCount = commandLine.parse(args);
		
		if (args.length - argCount != 1) {
			System.err.println("Usage: " + CountBugsFast.class.getName() +
					" [options] <bug collection>");
			System.err.println("Options:");
			commandLine.printUsage(System.err);
			System.exit(1);
		}
		
		CountBugsFast countBugs = new CountBugsFast(
				new BufferedInputStream(new FileInputStream(args[argCount])));
		if (commandLine.getAbbrevs() != null)
			countBugs.setAbbrevs(commandLine.getAbbrevs());
		if (commandLine.getCategories() != null)
			countBugs.setCategories(commandLine.getCategories());
		countBugs.setMinPriority(commandLine.getMinPriority());
		
		countBugs.execute();
		System.out.println(countBugs.getCount());
	}

}
