package fr.cnes.sonarqube.plugins.icode.settings;

import static java.util.Arrays.asList;

import java.util.List;

import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

import fr.cnes.sonarqube.plugins.icode.languages.ICodeLanguage;


public class ICodeLanguageProperties {
	
	/** Report sub directory */
	public static final String REPORT_SUBDIR_KEY = "icode."+ICodeLanguage.KEY+".report.subdir";
	public static final String REPORT_SUBDIR_DEFAULT_VALUE = "icode-reports";
	
	/** Report extension */
	public static final String REPORT_OUT_EXT_KEY = "icode."+ICodeLanguage.KEY+".report.out.ext";
	public static final String REPORT_OUT_EXT_DEFAULT_VALUE = ".res.xml";
	
	/** project code file patterns */
	public static final String EXPECTED_REPORT_INPUT_FILE_TYPES_KEY = "icode."+ICodeLanguage.KEY+".file.suffixes";
	public static final String EXPECTED_REPORT_INPUT_FILE_TYPES_DEFAULT_VALUE = "*.f,*.f77,*.f90,*.sh";
	public static final String FILE_SUFFIXES_SEPARATOR = ",";
	
	/**
	 * Plugin properties extensions
	 */
	public static List<PropertyDefinition> getProperties() {
		return asList(PropertyDefinition.builder(REPORT_SUBDIR_KEY)
				.defaultValue(REPORT_SUBDIR_DEFAULT_VALUE).category(ICodeLanguage.NAME)
				.name("Report subdirectory").description("Name of ICode output report subdirectory.")
				// .onQualifiers(Qualifiers.PROJECT)
				.build(),
				PropertyDefinition.builder(REPORT_OUT_EXT_KEY)
				.defaultValue(REPORT_OUT_EXT_DEFAULT_VALUE).category(ICodeLanguage.NAME)
				.name("Report file Suffixes").description("The report file have the same name as source file followed by this extension name.")
				// .onQualifiers(Qualifiers.PROJECT)
				.build(),
				PropertyDefinition.builder(EXPECTED_REPORT_INPUT_FILE_TYPES_KEY)
				.defaultValue(EXPECTED_REPORT_INPUT_FILE_TYPES_DEFAULT_VALUE).category(ICodeLanguage.NAME)
				.name("File Suffixes").description("Comma-separated list of suffixes for files to analyze.")
				// .onQualifiers(Qualifiers.PROJECT)
				.build());
	}

}