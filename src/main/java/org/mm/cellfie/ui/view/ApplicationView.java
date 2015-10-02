package org.mm.cellfie.ui.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.border.EmptyBorder;

import org.mm.app.MMApplication;
import org.mm.app.MMApplicationFactory;
import org.mm.app.MMApplicationModel;
import org.mm.core.TransformationRule;
import org.mm.core.TransformationRuleSet;
import org.mm.core.settings.ReferenceSettings;
import org.mm.core.settings.ValueEncodingSetting;
import org.mm.parser.ASTExpression;
import org.mm.parser.MappingMasterParser;
import org.mm.parser.ParseException;
import org.mm.parser.SimpleNode;
import org.mm.parser.node.ExpressionNode;
import org.mm.parser.node.MMExpressionNode;
import org.mm.renderer.Renderer;
import org.mm.rendering.Rendering;
import org.mm.ss.SpreadSheetDataSource;
import org.mm.ui.DialogManager;
import org.mm.ui.ModelView;
import org.protege.editor.core.ui.split.ViewSplitPane;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * This is the main Mapping Master user interface. It contains a view of a
 * spreadsheet and a control area to edit and execute Mapping Master rules.
 */
public class ApplicationView extends JPanel implements ModelView
{
   private static final long serialVersionUID = 1L;

   private OWLOntology ontology;
   private OWLEditorKit editorKit;

   private DialogManager applicationDialogManager;
   private DataSourceView dataSourceView;
   private TransformationRuleBrowserView transformationRuleBrowserView;

   private RenderLogging renderLogging;

   private MMApplication application;
   private MMApplicationFactory applicationFactory = new MMApplicationFactory();

   public ApplicationView(OWLOntology ontology, String workbookFilePath, OWLEditorKit editorKit, DialogManager applicationDialogManager)
   {
      this.ontology = ontology;
      this.editorKit = editorKit;
      this.applicationDialogManager = applicationDialogManager;

      setLayout(new BorderLayout());

      JPanel pnlTargetOntology = new JPanel(new FlowLayout(FlowLayout.LEFT));
      pnlTargetOntology.setBorder(new EmptyBorder(5, 5, 0, 5));
      add(pnlTargetOntology, BorderLayout.NORTH);

      JLabel lblTargetOntology = new JLabel("Target Ontology: ");
      lblTargetOntology.setForeground(Color.DARK_GRAY);
      pnlTargetOntology.add(lblTargetOntology);

      JLabel lblOntologyID = new JLabel(getTitle(ontology));
      lblOntologyID.setForeground(Color.DARK_GRAY);
      pnlTargetOntology.add(lblOntologyID);

      ViewSplitPane splitPane = new ViewSplitPane(JSplitPane.VERTICAL_SPLIT);
      splitPane.setResizeWeight(0.4);
      add(splitPane, BorderLayout.CENTER);

      loadWorkbookDocument(workbookFilePath);
//      loadMappingDocument(mappingFilePath) // XXX In case the UI will allow users to input mapping file in advance
      setupApplication();

      /*
       * Workbook sheet GUI presentation
       */
      dataSourceView = new DataSourceView(this);
      splitPane.setTopComponent(dataSourceView);

      /*
       * Mapping browser, create, edit, remove panel
       */
      transformationRuleBrowserView = new TransformationRuleBrowserView(this);
      splitPane.setBottomComponent(transformationRuleBrowserView);

      validate();
   }

   private String getTitle(OWLOntology ontology)
   {
      IRI ontologyID = ontology.getOntologyID().getOntologyIRI();
      return String.format("%s (%s)", ontologyID.getFragment(), ontologyID);
   }

   private void loadWorkbookDocument(String path)
   {
      applicationFactory.setWorkbookLocation(path);
   }

   public String getWorkbookFileLocation()
   {
      return applicationFactory.getWorkbookLocation();
   }

   public void loadMappingDocument(String path)
   {
      setRuleFileLocation(path);
      setupApplication();
      transformationRuleBrowserView.update();
   }

   public String getRuleFileLocation()
   {
      return applicationFactory.getMappingLocation();
   }

   public void setRuleFileLocation(String path)
   {
      applicationFactory.setMappingLocation(path);
   }

   private void setupApplication()
   {
      try {
         application = applicationFactory.createApplication(getActiveOntology());
      } catch (Exception e) {
         applicationDialogManager.showErrorMessageDialog(this, "Initialization error: " + e.getMessage());
      }
   }

   private MMApplicationModel getApplicationModel()
   {
      return application.getApplicationModel();
   }

   public void initLogging()
   {
      renderLogging = new RenderLogging();
      renderLogging.init();
   }

   public RenderLogging getRenderLogging()
   {
      return renderLogging;
   }

   public void evaluate(TransformationRule rule, Renderer renderer, Set<Rendering> results) throws ParseException
   {
      final ReferenceSettings referenceSettings = new ReferenceSettings();

      String ruleString = rule.getRuleString();
      MMExpressionNode ruleNode = parseRule(ruleString, referenceSettings).getMMExpressionNode();
      Optional<? extends Rendering> renderingResult = renderer.render(ruleNode);
      if (renderingResult.isPresent()) {
         results.add(renderingResult.get());
      }
   }

   public void log(TransformationRule rule, Renderer renderer, RenderLogging logging) throws ParseException
   {
      final ReferenceSettings referenceSettings = new ReferenceSettings();
      referenceSettings.setValueEncodingSetting(ValueEncodingSetting.RDFS_LABEL);

      String ruleString = rule.getRuleString();
      MMExpressionNode ruleNode = parseRule(ruleString, referenceSettings).getMMExpressionNode();
      Optional<? extends Rendering> renderingResult = renderer.render(ruleNode);
      if (renderingResult.isPresent()) {
         logging.append(renderingResult.get().getRendering());
      }
   }

   private ExpressionNode parseRule(String ruleString, ReferenceSettings settings) throws ParseException
   {
      MappingMasterParser parser = new MappingMasterParser(new ByteArrayInputStream(ruleString.getBytes()), settings, -1);
      SimpleNode simpleNode = parser.expression();
      return new ExpressionNode((ASTExpression) simpleNode);
   }

   @Override
   public void update()
   {
      // NO-OP
   }

   public OWLOntology getActiveOntology()
   {
      return ontology;
   }

   public SpreadSheetDataSource getActiveWorkbook()
   {
      return getApplicationModel().getDataSourceModel().getDataSource();
   }

   public List<TransformationRule> getActiveTransformationRules()
   {
      return getApplicationModel().getTransformationRuleModel().getRules();
   }

   public void updateTransformationRuleModel(List<TransformationRule> rules)
   {
      TransformationRuleSet ruleSet = TransformationRuleSet.create(rules);
      getApplicationModel().getTransformationRuleModel().changeTransformationRuleSet(ruleSet);
   }

   public Renderer getDefaultRenderer()
   {
      return getApplicationModel().getDefaultRenderer();
   }

   public Renderer getLogRenderer()
   {
      return getApplicationModel().getLogRenderer();
   }

   public OWLEditorKit getEditorKit()
   {
      return editorKit;
   }

   public DialogManager getApplicationDialogManager()
   {
      return applicationDialogManager;
   }

   public DataSourceView getDataSourceView()
   {
      return dataSourceView;
   }

   public TransformationRuleBrowserView getMappingBrowserView()
   {
      return transformationRuleBrowserView;
   }

   class RenderLogging
   {
      private DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");

      private String logFileLocation;
      private StringBuffer logMessage = new StringBuffer();
      private File logFile;

      private final Logger LOG = Logger.getLogger(RenderLogging.class.getName()); 

      public void init()
      {
         clearLog();
         
         String mappingFilePath = applicationFactory.getMappingLocation();
         if (mappingFilePath == null) {
            logFileLocation = getDefaultLogFileLocation();
         } else {
            logFileLocation = getLogFileLocation(new File(mappingFilePath));
         }
         String timestamp = dateFormat.format(new Date());
         String fileName = String.format("%s%s.log", logFileLocation, timestamp);
         logFile = new File(fileName);
         
         LOG.info("Cellfie log file: " + fileName);
      }

      public RenderLogging append(String message)
      {
         logMessage.append(message);
         return this;
      }

      public void save() throws FileNotFoundException
      {
         PrintWriter printer = new PrintWriter(logFile);
         printer.print(flushLog());
         printer.close();
         clearLog();
      }

      public InputStreamReader load() throws FileNotFoundException
      {
         return new FileReader(logFile);
      }

      private String getLogFileLocation(File mappingFile)
      {
         String mappingPath = mappingFile.getParent();
         String mappingFileName = mappingFile.getName().substring(0, mappingFile.getName().lastIndexOf("."));
         return mappingPath + System.getProperty("file.separator") + mappingFileName + "_mmexec";
      }

      private String getDefaultLogFileLocation()
      {
         String tmpPath = System.getProperty("java.io.tmpdir");
         return tmpPath + "mmexec";
      }

      private void clearLog()
      {
         logMessage.setLength(0);
      }

      private String flushLog()
      {
         return logMessage.toString();
      }
   }
}
