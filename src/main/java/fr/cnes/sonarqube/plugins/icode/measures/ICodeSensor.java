package fr.cnes.sonarqube.plugins.icode.measures;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import fr.cnes.sonarqube.plugins.icode.report.XmlReportReader;


/**
 * Scan ICode report file.
 * For all project code file : <b>FILE</b>, ICode create a report file <b>FILE{@link ICodeSensor#REPORT_EXT}</b> into the {@link ICodeSensor#REPORT_SUBDIR} shall be
 * 
 * @author Cyrille FRANCOIS
 */
public class ICodeSensor implements Sensor {
	
	private static final Logger LOGGER = Loggers.get(ICodeSensor.class);
	
	/** Report sub directory */
	public static final String REPORT_SUBDIR = "reports";
	/** Report extension */
	public static final String REPORT_EXT = ".res.xml";
	/** project code file patterns */
	public static final String EXPECTED_REPORT_INPUT_FILE_TYPES = "*.f,*.f77,*.f90";
	
	@Override
	public void describe(SensorDescriptor descriptor) {
		descriptor.name(getClass().getName());
	}

	@Override
	public void execute(SensorContext context) {
	    LOGGER.info("ICodeSensor is running...");
	    FileSystem fs = context.fileSystem();
	    FilePredicates p = fs.predicates();
//	    // only "main" files, but not "tests"
//	    Iterable<InputFile> files = fs.inputFiles(fs.predicates().hasType(InputFile.Type.MAIN));
	    String[] icodeMatchingPatterns = matchingPatterns();
	    Iterable<InputFile> filesC = fs.inputFiles(fs.predicates().matchesPathPatterns(icodeMatchingPatterns));
	    for (InputFile file : filesC) {

  	      // Check for report out
      	  String fileRelativePathNameReportOut = outReportFileName(file);

      	  // Analyse report out
      	 analyseReportOut(context, file, fileRelativePathNameReportOut);
		}
	    LOGGER.info("ICodeSensor done!");
	}

	/**
	 * @return all expected file code patterns
	 */
	private String[] matchingPatterns() {
		StringBuffer sb = new StringBuffer();
		String patternSeparator = ",";
		String[] res = EXPECTED_REPORT_INPUT_FILE_TYPES.trim().split(patternSeparator);
		return res;
	}

	/**
	 * @param file input code file
	 * @return relative report file for this input code file
	 */
	protected String outReportFileName(InputFile file) {		
		String reportOutExt = REPORT_EXT;
		return relativeReportFileName(file, reportOutExt);
	}

	/**
	 * @param file input code file
	 * @param reportOutExt report file extension
	 * @return relative report file for this input code file
	 */
	private String relativeReportFileName(InputFile file, String reportOutExt) {
		String separator = file.file().separator;
		String name = file.file().getName();
		return REPORT_SUBDIR+separator+name+reportOutExt;
	}

	/**
	 *  Analyze a report file provided by the external tool ICode.
	 *  Check the report file integrity (exist, not empty and readable)
	 *  
	 * @param context Sonar sensor context
	 * @param file input code file 
	 * @param fileRelativePathNameReportOut name of the expected report file for this input code file
	 */
	private void analyseReportOut(
			SensorContext context, 
			InputFile file, 
			String fileRelativePathNameReportOut) {
		ReportInterface report = null;
		StringBuffer warningMsgs = new StringBuffer();
		int nbWarningMsgs = 0;
		StringBuffer errorMsgs = new StringBuffer();
		int nbErrorMsgs = 0;
		LOGGER.debug("file.absolutePath():"+file.absolutePath());
		LOGGER.debug("Paths.get(file.absolutePath()).getParent():"+Paths.get(file.absolutePath()).getParent());
		LOGGER.debug("fileRelativePathNameReportOut:"+fileRelativePathNameReportOut);
		Path fileReportPath = Paths.get(file.absolutePath()).getParent().resolve(fileRelativePathNameReportOut);
	    if(existReportFile(fileReportPath)){
  	    	  
			try {
				FileChannel reportFile = FileChannel.open(fileReportPath);
				report = XmlReportReader.parse(fileReportPath);
			    long reportFileSize = reportFile.size();
			    if(reportFileSize>0){
			    	errorMsgs.append("Empty report file : "+fileRelativePathNameReportOut);
			    	nbErrorMsgs++;
			    }
			    if(report == null){
			    	errorMsgs.append("Report file : "+fileRelativePathNameReportOut+" cannot be analysed !");
			    	nbErrorMsgs++;
			    }
			} catch (IOException e) {
				errorMsgs.append("Unexpected error report file for : "+fileRelativePathNameReportOut);
		    	nbErrorMsgs++;
			}
	    }
	    else{
	    	errorMsgs.append("No report file for : "+fileRelativePathNameReportOut);
	    	nbErrorMsgs++;
	    }
		// Add a ICode report warning
		if(nbWarningMsgs>0){
		      context.<String>newMeasure()
		        .forMetric(ICodeMetrics.REPORT_FILES_WARNING)
		        .on(file)
		        .withValue(warningMsgs.toString())
		        .save();	    	  			
		      context.<Integer>newMeasure()
		        .forMetric(ICodeMetrics.NUMBER_OF_WARNINGS)
		        .on(file)
		        .withValue(nbWarningMsgs)
		        .save();	    	  			
		}
		// Add a ICode report error
		if(nbErrorMsgs>0){
		      context.<String>newMeasure()
		        .forMetric(ICodeMetrics.REPORT_FILES_ERROR)
		        .on(file)
		        .withValue(errorMsgs.toString())
		        .save();	    	  			
		      context.<Integer>newMeasure()
		        .forMetric(ICodeMetrics.NUMBER_OF_ERRORS)
		        .on(file)
		        .withValue(nbErrorMsgs)
		        .save();	    	  			
		}
		if(report != null){
			parseReportMeasures(context, file, report);
			parseReportIssues(context, file, report);
		}
	}

	/**
	 * Parse all measures from a valid report file.
	 * Measures shall be defined by Metrics {@link ICodeMetric}
	 * Only one measure by file and Metrics
	 * Measures by project are computed by {@link MeasureComputer#compute}
	 * 
	 * @param context Sonar sensor context
	 * @param file input code file 
	 * @param report report file analyzed
	 * 
	 * @see XmlReportReader#parse
	 */
	private void parseReportMeasures(SensorContext context, InputFile file, ReportInterface report) {
		// Add metrics results
		ReportModuleRuleInterface reportModuleRuleInterface = report.getModuleCyclomaticMeasure();
		ReportFunctionRuleInterface[] reportModuleRuleInterfaces = report.getCyclomaticMeasureByFunction();
		double cyclomaticValueSum = 0;
		double cyclomaticValueMin = Double.MAX_VALUE;
		double cyclomaticValueMax = 0;
		
		// Create module measures (from each function measures provided by ICode)
		if(reportModuleRuleInterfaces != null){
			
			// Read measure value for each elements of this module
			for (ReportFunctionRuleInterface currentFunctionRuleInterface : reportModuleRuleInterfaces) {
				try {							
					double currentValue = Double.valueOf(currentFunctionRuleInterface.getValue());
					
					// Sum all elements values for mean computation
					cyclomaticValueSum += currentValue;
					
					// Search maximum
					if(currentValue > cyclomaticValueMax){
						cyclomaticValueMax = currentValue;
					}
					
					// Search minimum
					if(currentValue < cyclomaticValueMin){
						cyclomaticValueMin = currentValue;
					}
				} catch (Exception e) {
					LOGGER.error("No cyclomatic measure: "+currentFunctionRuleInterface.getValue());
				}
			}
			
			// Create measure for this file
			double cyclomaticValueMean = (reportModuleRuleInterfaces.length>0)?(cyclomaticValueSum/reportModuleRuleInterfaces.length):cyclomaticValueSum;
			
			// Complexity simplified store by module is not defined by ICode, but ICode sonar plugin expected a module measure... 
			String cyclomaticValue = reportModuleRuleInterface.getValue();
			if("NaN".equals(cyclomaticValue)){
				cyclomaticValue = ""+((int)cyclomaticValueSum);
			}
			else{
				LOGGER.warn("ICode define complexity simplified by module");
			}
			
			if(report.isF77()){
				storeCyclomaticMeasuresF77(context, file, cyclomaticValueMin, cyclomaticValueMax, cyclomaticValueMean,
						cyclomaticValue);
			}
			else if(report.isF90()){
				storeCyclomaticMeasuresF90(context, file, cyclomaticValueMin, cyclomaticValueMax, cyclomaticValueMean,
						cyclomaticValue);				
			}
			else{
				storeCyclomaticMeasuresSHELL(context, file, cyclomaticValueMin, cyclomaticValueMax, cyclomaticValueMean,
						cyclomaticValue);				
			}
		}

	}
	
	/**
	 * Store measures from a valid report file into F77 Metrics.
	 * 
	 * @param context Sonar sensor context
	 * @param file input code file 
	 * @param cyclomaticValueMin minimum
	 * @param cyclomaticValueMax maximum
	 * @param cyclomaticValueMean mean
	 * @param value cyclomatic complexity value
	 * 
	 * @see ICodeMetrics
	 */
	private void storeCyclomaticMeasuresF77(SensorContext context, InputFile file, double cyclomaticValueMin,
			double cyclomaticValueMax, double cyclomaticValueMean, String value) {
		// Store module CYCLOMATIC, MEAN, MIN, MAX
		context.<Integer>newMeasure()
		.forMetric(ICodeMetrics.F77_CYCLOMATIC)
		.on(file)
		.withValue(Integer.valueOf(value))
		.save();
		context.<Integer>newMeasure()
		.forMetric(ICodeMetrics.F77_CYCLOMATIC_MAX)
		.on(file)
		.withValue(Integer.valueOf((int)cyclomaticValueMax))
		.save();
		context.<Integer>newMeasure()
		.forMetric(ICodeMetrics.F77_CYCLOMATIC_MIN)
		.on(file)
		.withValue(Integer.valueOf((int)cyclomaticValueMin))
		.save();
		context.<Double>newMeasure()
		.forMetric(ICodeMetrics.F77_CYCLOMATIC_MEAN)
		.on(file)
		.withValue(Double.valueOf(cyclomaticValueMean))
		.save();
	}
	
	/**
	 * Store measures from a valid report file into F90 Metrics.
	 * 
	 * @param context Sonar sensor context
	 * @param file input code file 
	 * @param cyclomaticValueMin minimum
	 * @param cyclomaticValueMax maximum
	 * @param cyclomaticValueMean mean
	 * @param value cyclomatic complexity value
	 * 
	 * @see ICodeMetrics
	 */
	private void storeCyclomaticMeasuresSHELL(SensorContext context, InputFile file, double cyclomaticValueMin,
			double cyclomaticValueMax, double cyclomaticValueMean, String cyclomaticValue) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Store measures from a valid report file into SHELL Metrics.
	 * 
	 * @param context Sonar sensor context
	 * @param file input code file 
	 * @param cyclomaticValueMin minimum
	 * @param cyclomaticValueMax maximum
	 * @param cyclomaticValueMean mean
	 * @param value cyclomatic complexity value
	 * 
	 * @see ICodeMetrics
	 */
	private void storeCyclomaticMeasuresF90(SensorContext context, InputFile file, double cyclomaticValueMin,
			double cyclomaticValueMax, double cyclomaticValueMean, String cyclomaticValue) {
		// TODO Auto-generated method stub
		
	}
	
	
	private void parseReportIssues(SensorContext context, InputFile file, ReportInterface report) {
		// Read all report issues
		ReportModuleRuleInterface reportModuleRuleInterface = report.getModuleCyclomaticMeasure();
		ReportFunctionRuleInterface[] reportModuleRuleInterfaces = report.getCyclomaticMeasureByFunction();

		// Create issues for this file
		if(reportModuleRuleInterface != null){
			InputFile inputFile = file;
			int lines = inputFile.lines();
			
			// Read measure value for each elements of this module
			for (ReportFunctionRuleInterface currentFunctionRuleInterface : reportModuleRuleInterfaces) {
				String line = currentFunctionRuleInterface.getLine();
	            int lineNr = getLineAsInt(line, lines);
//			            RuleKey ruleKey = ICodeRulesDefinition.RULE_CYCLO;//TODO: TBD
//			            NewIssue newIssue = context.newIssue().forRule(ruleKey);
//			            NewIssueLocation location = newIssue.newLocation()
//			                    .on(inputFile)
//			                    .at(inputFile.selectLine(lineNr > 0 ? lineNr : 1))
//			                    .message(currentFunctionRuleInterface.getValue());
//
//			            newIssue.at(location);
//			            newIssue.save();
//			            violationsCount++;//TODO: TBD count number of issues
			}
		}
	}

	/**
	 * Format line number according to the sonar expected format for issue
	 * 
	 * @param line string value of a line
	 * @param maxLine file line numbers
	 * @return Sonar complaint fine number (strictly positive)
	 */
	private static int getLineAsInt(String line, int maxLine) {
	    int lineNr = 0;
	    if (line != null) {
	      try {
	        lineNr = Integer.parseInt(line);
	        if (lineNr < 1) {
	          lineNr = 1;
	        } else if (lineNr > maxLine) {
	        	lineNr = maxLine;
	        }
	      } catch (java.lang.NumberFormatException nfe) {
	        LOGGER.warn("Skipping invalid line number: {}", line);
	        lineNr = -1;
	      }
	    }
	    return lineNr;
	}
	
	/**
	 * Check a expected report file.
	 * 
	 * @param fileReportPath
	 * @return true if the report file exist
	 */
	private static boolean existReportFile(Path fileReportPath) {
		boolean res=false;
		LOGGER.debug("existFile ?:"+fileReportPath.toAbsolutePath());
		res=Files.exists(fileReportPath, LinkOption.NOFOLLOW_LINKS);
		return res;
	}

}
