package es.unex.sextante.core;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.jfree.chart.ChartPanel;

import es.unex.sextante.additionalInfo.AdditionalInfoDataObject;
import es.unex.sextante.additionalInfo.AdditionalInfoFixedTable;
import es.unex.sextante.additionalInfo.AdditionalInfoMultipleInput;
import es.unex.sextante.additionalInfo.AdditionalInfoTableField;
import es.unex.sextante.dataObjects.I3DRasterLayer;
import es.unex.sextante.dataObjects.IDataObject;
import es.unex.sextante.dataObjects.ILayer;
import es.unex.sextante.dataObjects.IRasterLayer;
import es.unex.sextante.dataObjects.ITable;
import es.unex.sextante.dataObjects.IVectorLayer;
import es.unex.sextante.exceptions.GeoAlgorithmExecutionException;
import es.unex.sextante.exceptions.NullParameterAdditionalInfoException;
import es.unex.sextante.exceptions.NullParameterValueException;
import es.unex.sextante.exceptions.UnsupportedOutputChannelException;
import es.unex.sextante.exceptions.WrongAnalysisExtentException;
import es.unex.sextante.exceptions.WrongParameterIDException;
import es.unex.sextante.exceptions.WrongParameterTypeException;
import es.unex.sextante.outputs.FileOutputChannel;
import es.unex.sextante.outputs.IOutputChannel;
import es.unex.sextante.outputs.Output;
import es.unex.sextante.outputs.Output3DRasterLayer;
import es.unex.sextante.outputs.OutputChart;
import es.unex.sextante.outputs.OutputImage;
import es.unex.sextante.outputs.OutputNumericalValue;
import es.unex.sextante.outputs.OutputRasterLayer;
import es.unex.sextante.outputs.OutputTable;
import es.unex.sextante.outputs.OutputText;
import es.unex.sextante.outputs.OutputVectorLayer;
import es.unex.sextante.parameters.Parameter;
import es.unex.sextante.parameters.ParameterDataObject;
import es.unex.sextante.parameters.ParameterFixedTable;
import es.unex.sextante.parameters.ParameterMultipleInput;
import es.unex.sextante.parameters.ParameterTableField;
import es.unex.sextante.parameters.RasterLayerAndBand;


/**
 * A class defining a geo-algorithm
 * 
 * @author Victor Olaya volaya@unex.es
 * 
 */
public abstract class GeoAlgorithm {

   /**
    * The set of input parameters and their values
    */
   protected ParametersSet         m_Parameters;

   /**
    * The set of output objects. Results will be stored in this set once the execution is finished
    */
   protected OutputObjectsSet      m_OutputObjects;

   /**
    * A task to track progress of the algorithm
    */
   protected ITaskMonitor          m_Task;

   /**
    * The extent will be used to analyze layers and create create new output layers
    */
   protected AnalysisExtent        m_AnalysisExtent;

   /**
    * True if the algorithm generates output layers and its extent can be defined by the user
    */
   protected boolean               m_bUserCanDefineAnalysisExtent = true;

   /**
    * true if the number of steps needed to execute the algorithm can be known in advance
    */
   protected boolean               m_bIsDeterminatedProcess       = true;

   /**
    * The name of the algorithm
    */
   private String                  m_sName;

   /**
    * The name of the group this algorithm belongs to
    */
   private String                  m_sGroup;

   /**
    * A description string for misc. use
    */
   private String                  m_sDescription					= null; 
   
   
   /**
    * Color values useful for e.g. coloring algorithm nodes in the modeler
    */
   private int                  i_R									= 128;
   private int                  i_G									= 128;
   private int                  i_B									= 128;
   private int                  i_Alpha								= 255;
   
   /**
    * Process metadata
    */
   private HashMap                 m_ProcessMetadata;

   /**
    * Used to indicate if the extent has been set by the user or automatically by the algorithm
    */
   protected boolean               m_bIsAutoExtent;

   /**
    * The CRS to use if the algorithm generates new layers
    */
   protected Object                m_CRS;

   private long                    m_lInitTime;
   private long                    m_lEndTime;

   /**
    * The output factory to use for generating new data objects from this algorithm
    */
   protected OutputFactory         m_OutputFactory;

   /**
    * Alternative names for outputs
    */
   private HashMap<String, String> m_OutputMap;


   public GeoAlgorithm() {

      m_Parameters = new ParametersSet();
      m_OutputObjects = new OutputObjectsSet();

      defineCharacteristics();

   }


   /**
    * This method should be overridden and used to specify the parameters needed by the GeoAlgorithm, using the corresponding
    * methods of the ParametersSet object.
    * 
    * Also, output objects must be added, so SEXTANTE knows in advance which outputs will be generated by the algorithm. See the
    * addOutputXXXX family of methods ({@link #addOutputRasterLayer}, {@link #addOutputVectorLayer}, etc.)
    * 
    */
   public abstract void defineCharacteristics();


   /**
    * This method should be used to execute the algorithm once the parameters have been assigned.
    * 
    * @param task
    *                a ITaskMonitor to track the progress of the execution. If is null, a
    * @see {@link SilentTaskMonitor} will be used.
    * @param outputFactory
    *                The output factory to use to generate new data objects
    * @return true if the algorithm was correctly executed. False if it was canceled
    * @throws GeoAlgorithmExecutionException
    */
   public boolean execute(final ITaskMonitor task,
                          final OutputFactory outputFactory) throws GeoAlgorithmExecutionException {
      return execute(task, outputFactory, null);
   }


   /**
    * This method should be used to execute the algorithm once the parameters have been assigned.
    * 
    * @param task
    *                a ITaskMonitor to track the progress of the execution. If is null, a
    * @see {@link SilentTaskMonitor} will be used.
    * @param outputFactory
    *                The output factory to use to generate new data objects
    * @param outputMap
    *                Maps {@link IDataObject} algorithm output names to the desired output name. At IDataObject output creation,
    *                {@link OutputFactory} implementations will receive the values in this map as names.
    * @return true if the algorithm was correctly executed. False if it was canceled
    * @throws GeoAlgorithmExecutionException
    */
   public boolean execute(final ITaskMonitor task,
                          final OutputFactory outputFactory,
                          final HashMap<String, String> outputMap) throws GeoAlgorithmExecutionException {

      final StringBuffer sb = new StringBuffer();

      if (task == null) {
         m_Task = new SilentTaskMonitor();
      }
      else {
         m_Task = task;
         m_Task.setProgressText("");
      }
      m_OutputFactory = outputFactory;

      initInputDataObjects();

      if (!adjustOutputExtent()) {
         throw new WrongAnalysisExtentException();
      }

      final DateFormat formatter = DateFormat.getDateTimeInstance();

      final String[] cmd = this.getAlgorithmAsCommandLineSentences();

      if (cmd != null) {
         for (final String element : cmd) {
            sb.append("Executing command: " + element + "\n");
         }
      }

      m_OutputMap = outputMap;

      m_lInitTime = System.currentTimeMillis();
      sb.append("Starting algorithm execution...:" + formatter.format(new Date(m_lInitTime)) + "\n");

      final boolean bReturn = processAlgorithm();

      m_lEndTime = System.currentTimeMillis();
      sb.append("Finished algorithm execution:" + formatter.format(new Date(m_lEndTime)) + "\n");
      sb.append("Execution time (millisecs):" + Long.toString(m_lEndTime - m_lInitTime) + "\n");

      Sextante.addInfoToLog(sb.toString());

      closeInputDataObjects();
      createProcessMetadata();

      if (bReturn) {
         postProcessOutputDataObjects();
      }
      else {
         sb.append("Algorithm was canceled!!\n");
      }

      return bReturn;

   }


   /**
    * Creates metadata for the process once it has been executed
    */
   private void createProcessMetadata() {

      m_ProcessMetadata = new HashMap();

      m_ProcessMetadata.put("PROCESS_DATE", new Date(m_lInitTime));
      m_ProcessMetadata.put("PROCESS_DURATION", new Long(m_lEndTime - m_lInitTime));

      //TODO: add more metadata

   }


   /**
    * Returns a map with metadata entries. It will return an empty map if the process has not been executed yet.
    * 
    * @return the metadata of the process.
    */
   public HashMap getProcessMetadata() {

      return m_ProcessMetadata;

   }


   /**
    * post-processes all output objects
    * 
    */
   private void postProcessOutputDataObjects() throws GeoAlgorithmExecutionException {

      try {
         for (int i = 0; i < m_OutputObjects.getOutputObjectsCount(); i++) {
            final Output out = m_OutputObjects.getOutput(i);
            final Object obj = out.getOutputObject();
            if (obj instanceof IDataObject) {
               final IDataObject dataObject = (IDataObject) obj;
               dataObject.postProcess();
            }
         }
      }
      catch (final Exception e) {
         Sextante.addErrorToLog(e);
         throw new GeoAlgorithmExecutionException(e.getMessage());
      }

   }


   /**
    * Closes all input data objects when they are not needed anymore
    */
   private void closeInputDataObjects() {

      for (int i = 0; i < m_Parameters.getNumberOfParameters(); i++) {
         try {
            final Parameter param = m_Parameters.getParameter(i);
            final Object obj = param.getParameterValueAsObject();
            if (obj instanceof ILayer) {
               final ILayer layer = (ILayer) obj;
               layer.close();
            }
            else if (obj instanceof ITable) {
               final ITable table = (ITable) obj;
               table.close();
            }
            else if (obj instanceof ArrayList) {
               final ArrayList list = (ArrayList) obj;
               for (int j = 0; j < list.size(); j++) {
                  closeInputDataObject(list.get(j));
               }
            }
         }
         catch (final Exception e) {
            Sextante.addErrorToLog(e);
         }
      }

   }


   /**
    * Closes an input object
    * 
    * @param obj
    *                the input object to close
    */
   private void closeInputDataObject(final Object obj) {

      if (obj instanceof ILayer) {
         final ILayer layer = (ILayer) obj;
         layer.close();
      }
      else if (obj instanceof ITable) {
         final ITable table = (ITable) obj;
         table.close();
      }
      else if (obj instanceof RasterLayerAndBand) {
         final RasterLayerAndBand rab = (RasterLayerAndBand) obj;
         final IRasterLayer layer = rab.getRasterLayer();
         layer.close();
      }

   }


   /**
    * Opens data objects before using them. It also sets the CRS for output layers as the CRS of the first valid input layers, or
    * the default one if no layer is used. (see {@link OuputFactory#getDefualtCRS}). Sextante does not perform any coordinate
    * transformation, and all input layers are assumed to have the same CRS, which will be the one assigned to all output layers.
    */
   private void initInputDataObjects() {

      for (int i = 0; i < m_Parameters.getNumberOfParameters(); i++) {
         final Parameter param = m_Parameters.getParameter(i);
         final Object obj = param.getParameterValueAsObject();
         initInputDataObject(obj);
      }

      if (m_CRS == null) {
         m_CRS = m_OutputFactory.getDefaultCRS();
      }
   }


   /**
    * Opens a single data objects before using it. It also sets the CRS for output layers if a layer is passed and the current
    * output CRS is not set.
    */
   private void initInputDataObject(final Object obj) {

      if (obj instanceof ILayer) {
         final ILayer layer = (ILayer) obj;
         layer.open();
         if (m_CRS == null) {
            m_CRS = layer.getCRS();
         }
         else {
            if ((layer.getCRS() == null) || !layer.getCRS().equals(m_CRS)) {
               Sextante.addWarningToLog("Distintos_CRS");
            }
         }
      }
      else if (obj instanceof ITable) {
         final ITable table = (ITable) obj;
         table.open();
      }
      else if (obj instanceof RasterLayerAndBand) {
         final RasterLayerAndBand rlab = (RasterLayerAndBand) obj;
         final IRasterLayer layer = rlab.getRasterLayer();
         layer.open();
         if (m_CRS == null) {
            m_CRS = layer.getCRS();
         }
      }
      else if (obj instanceof ArrayList) {
         final ArrayList list = (ArrayList) obj;
         for (int j = 0; j < list.size(); j++) {
            initInputDataObject(list.get(j));
         }
      }

   }


   /**
    * This method should implement the algorithm itself, using the values of the parameters and processing them.
    * 
    * @return true if the algorithm was correctly executed. False if it was canceled.
    * @throws GeoAlgorithmExecutionException
    *                 if there were problems during algorithm execution
    */
   public abstract boolean processAlgorithm() throws GeoAlgorithmExecutionException;


   /**
    * This method sets the output extent according to input layers, in case it hasn't been set.
    * 
    * To do so, it takes the minimum extent that covers all input layers, and the minimum cellsize in case there are input raster
    * layers.
    * 
    * @return false if there are not enough layers to adjust the output extent for running this algorithm
    */
   public boolean adjustOutputExtent() {

      boolean bRasterLayers = false;
      boolean bLayers = false;
      boolean bOK = true;
      IRasterLayer rasterLayer;
      RasterLayerAndBand rlab;
      IVectorLayer vectorLayer;
      ArrayList layers;
      int i, j;

      if (!getUserCanDefineAnalysisExtent()) {
         m_bIsAutoExtent = false;
         return true;
      }

      if (m_bIsAutoExtent && (m_AnalysisExtent != null)) {
         return true;
      }

      if (m_AnalysisExtent == null) {
         m_bIsAutoExtent = true;
         for (i = 0; i < m_Parameters.getNumberOfParameters(); i++) {
            try {
               if (m_Parameters.getParameter(i).getParameterTypeName().equals("Raster Layer")) {
                  rasterLayer = m_Parameters.getParameterValueAsRasterLayer(m_Parameters.getParameter(i).getParameterName());
                  if (rasterLayer != null) {
                     if (!bRasterLayers) {
                        bLayers = true;
                        bRasterLayers = true;
                        m_AnalysisExtent = new AnalysisExtent(rasterLayer);
                     }
                     else {
                        if (!bRasterLayers) {
                           m_AnalysisExtent.setCellSize(rasterLayer.getLayerGridExtent().getCellSize());
                        }
                        m_AnalysisExtent.addExtent(rasterLayer.getLayerGridExtent());
                     }
                  }
               }
               else if (m_Parameters.getParameter(i).getParameterTypeName().equals("Vector Layer")) {
                  vectorLayer = m_Parameters.getParameterValueAsVectorLayer(m_Parameters.getParameter(i).getParameterName());
                  if (vectorLayer != null) {
                     if (!bLayers) {
                        bLayers = true;
                        m_AnalysisExtent = new AnalysisExtent(vectorLayer);
                     }
                     else {
                        m_AnalysisExtent.addExtent(vectorLayer.getFullExtent());
                     }
                  }
               }
               else if (m_Parameters.getParameter(i).getParameterTypeName().equals("Multiple Input")) {
                  final AdditionalInfoMultipleInput additionalInfo = (AdditionalInfoMultipleInput) m_Parameters.getParameter(i).getParameterAdditionalInfo();
                  if (additionalInfo.getDataType() == AdditionalInfoMultipleInput.DATA_TYPE_RASTER) {
                     layers = m_Parameters.getParameterValueAsArrayList(m_Parameters.getParameter(i).getParameterName());
                     for (j = 0; j < layers.size(); j++) {
                        rasterLayer = (IRasterLayer) layers.get(j);
                        if (!bLayers) {
                           bLayers = true;
                           bRasterLayers = true;
                           m_AnalysisExtent = new AnalysisExtent(rasterLayer);
                        }
                        else {
                           m_AnalysisExtent.addExtent(rasterLayer.getLayerGridExtent());
                        }
                     }
                  }
                  if (additionalInfo.getDataType() == AdditionalInfoMultipleInput.DATA_TYPE_BAND) {
                     layers = m_Parameters.getParameterValueAsArrayList(m_Parameters.getParameter(i).getParameterName());
                     for (j = 0; j < layers.size(); j++) {
                        rlab = (RasterLayerAndBand) layers.get(j);
                        rasterLayer = rlab.getRasterLayer();
                        if (!bRasterLayers) {
                           bLayers = true;
                           bRasterLayers = true;
                           m_AnalysisExtent = new AnalysisExtent(rasterLayer);
                        }
                        else {
                           m_AnalysisExtent.addExtent(rasterLayer.getLayerGridExtent());
                        }
                     }
                  }
                  if ((additionalInfo.getDataType() == AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_ANY)
                      || (additionalInfo.getDataType() == AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_POINT)
                      || (additionalInfo.getDataType() == AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_LINE)
                      || (additionalInfo.getDataType() == AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_POLYGON)) {
                     layers = m_Parameters.getParameterValueAsArrayList(m_Parameters.getParameter(i).getParameterName());
                     for (j = 0; j < layers.size(); j++) {
                        vectorLayer = (IVectorLayer) layers.get(j);
                        if (!bLayers) {
                           bLayers = true;
                           m_AnalysisExtent = new AnalysisExtent(vectorLayer);
                        }
                        else {
                           m_AnalysisExtent.addExtent(vectorLayer.getFullExtent());
                        }
                     }
                  }
               }
            }
            catch (final ArrayIndexOutOfBoundsException e) {
               Sextante.addErrorToLog(e);
            }
            catch (final WrongParameterTypeException e) {
               Sextante.addErrorToLog(e);
            }
            catch (final WrongParameterIDException e) {
               Sextante.addErrorToLog(e);
            }
            catch (final NullParameterValueException e) {
               Sextante.addErrorToLog(e);
            }
            catch (final NullParameterAdditionalInfoException e) {
               Sextante.addErrorToLog(e);
            }
         }
         if (m_OutputObjects.getOutputLayersCount() != 0) {
            bOK = bOK && bLayers;
         }
         if (m_OutputObjects.getRasterLayersCount() != 0) {
            bOK = bOK && bRasterLayers;
         }
      }
      else {
         bOK = true;
         m_bIsAutoExtent = false;
      }

      return bOK;

   }


   /**
    * Returns the name of the algorithm
    * 
    * @return The algorithm name. Might return different strings depending on the current SEXTANTE language
    */
   public String getName() {

      return m_sName;

   }


   /**
    * Sets the algorithm name. Use this in the defineCharacteristics() method Use
    * 
    * @see {@link Sextante#getText(String)} to support internationalization
    * @param sName
    *                the name of the algorithm
    */
   public void setName(final String sName) {

      m_sName = sName;

   }


   /**
    * Returns the group this algorithm belongs to
    * 
    * @return The group to which the algorithm belongs. Might return different strings depending on the current SEXTANTE language
    */
   public String getGroup() {

      return m_sGroup;

   }


   /**
    * Sets the name of the group this algorithm belongs to. Use this in the defineCharacteristics() method Use
    * 
    * @see {@link Sextante#getText(String)} to support internationalization
    * @param sGroup
    *                the name of the group
    */
   public void setGroup(final String sGroup) {

      m_sGroup = sGroup;

   }


   /**
    * Returns the description string of the algorithm
    * 
    * @return The description. This string is user-definable and e.g. used by the graphical modeler to label processing nodes.
    */
   public String getDescription() {

      return m_sDescription;

   }


   public int getColorR() {
	   return(i_R);
   }
   
   public int getColorG() {
	   return(i_G);
   }
  
   public int getColorB() {
	   return(i_B);
   }
   
   public int getColorAlpha() {
	   return(i_Alpha);
   }  
   
   
   /**
    * Sets the algorithm's description string. null by default.
    * This string is e.g. used by the graphical modeler to label processing nodes.
    * 
    * @param sDescription
    *                description string to be associated with this algorithm
    */
   public void setDescription(final String sDescription) {

      m_sDescription = sDescription;

   }   
   
   public void setColorR(final int red) {
	   i_R = red;
   }
   
   public void setColorG(final int green) {
	   i_G = green;
   }   
   
   public void setColorB(final int blue) {
	   i_B = blue;
   }   
   
   public void setColorAlpha(final int alpha) {
	   i_Alpha = alpha;
   }
   
   /**
    * 
    * @return the set of parameters needed to run the algorithm
    */
   public ParametersSet getParameters() {

      return m_Parameters;

   }


   /**
    * sets a new set of parameters for the algorithm
    * 
    * @param parameters
    *                the new set of parameters
    */
   public void setParameters(final ParametersSet parameters) {

      m_Parameters = parameters;

   }


   /**
    * Returns the extent that will be used to analyse layers and create create new output layers
    * 
    * @return The extent that will be used to analyse layers and create create new output layers
    */
   public AnalysisExtent getAnalysisExtent() {

      return m_AnalysisExtent;

   }


   /**
    * Sets a new extent will be used to analyse layers and create create new output layers
    * 
    * @param extent
    *                the new extent that will be used to analyse layers and create create new output layers
    */
   public void setAnalysisExtent(final AnalysisExtent extent) {

      m_AnalysisExtent = extent;

   }


   /**
    * Sets the current step in the progress bar, based on the ratio between the current task step and the total number of steps
    * needed to complete the task
    * 
    * @param iStep
    *                the current step
    * @param iTotalNumberOfSteps
    *                the total number of steps
    * @return true if the task hasn't been canceled
    */
   protected boolean setProgress(final int iStep,
                                 final int iTotalNumberOfSteps) {

      m_Task.setProgress(iStep, iTotalNumberOfSteps);
      return !m_Task.isCanceled();

   }


   /**
    * Sets the current progress text. Use this to inform the user about the work that the algorithm is currently doing.
    * 
    * @param sText
    *                the new progress text
    */
   protected void setProgressText(final String sText) {

      m_Task.setProgressText(sText);

   }


   /**
    * 
    * @return true if the algorithm requires parameters other than input layers or tables
    */
   public boolean requiresNonDataObjects() {

      return m_Parameters.requiresNonDataObjects();

   }


   /**
    * @return The number of parameters needed to run the algorithm
    */
   public int getNumberOfParameters() {

      return m_Parameters.getNumberOfParameters();

   }


   /**
    * @return The number of raster layers needed to run the algorithm
    */
   public int getNumberOfRasterLayers(final boolean includeMultipleInputs) {

      return m_Parameters.getNumberOfRasterLayers(includeMultipleInputs);

   }


   /**
    * @return The number of vector layer needed to run the algorithm
    */
   public int getNumberOfVectorLayers(final boolean includeMultipleInputs) {

      return m_Parameters.getNumberOfVectorLayers(includeMultipleInputs);

   }


   /**
    * @return The number of point vector layer needed to run the algorithm
    */
   public int getNumberOfPointVectorLayers() {

      return m_Parameters.getNumberOfPointVectorLayers();

   }


   /**
    * @return The number of line layer needed to run the algorithm
    */
   public int getNumberOfLineVectorLayers() {

      return m_Parameters.getNumberOfLineVectorLayers();

   }


   /**
    * @return The number of polygon vector layer needed to run the algorithm
    */
   public int getNumberOfPolygonLayers() {

      return m_Parameters.getNumberOfPolygonVectorLayers();

   }


   /**
    * @return The number of tables needed to run the algorithm
    */
   public int getNumberOfTables() {

      return m_Parameters.getNumberOfTables();

   }


   /**
    * @return The number of tables needed to run the algorithm
    */
   public int getNumberOfNoDataParameters() {

      return m_Parameters.getNumberOfNoDataParameters();

   }


   /**
    * @return The number of TableFields needed to run the algorithm
    */
   public int getNumberOfTableFieldsParameters() {

      return m_Parameters.getNumberOfTableFieldsParameters();

   }


   /**
    * @return The number of TableFields needed to run the algorithm
    */
   public int getNumberOfBandsParameters() {

      return m_Parameters.getNumberOfBandsParameters();

   }


   /**
    * 
    * @return true if the user can define the analysis region to be used by the algorithm
    */
   public boolean getUserCanDefineAnalysisExtent() {

      return m_bUserCanDefineAnalysisExtent;

   }


   /**
    * Use this method to indicate that the user can define the analysis region extent. When creating the corresponding panel for
    * the algorithm, it will contain a "Output region" tab, so the user can define the extent he prefers
    * 
    * @param bUserCanDefineAnalysisExtent
    *                true if the algorithm generates new raster layers
    */
   public void setUserCanDefineAnalysisExtent(final boolean bUserCanDefineAnalysisExtent) {

      m_bUserCanDefineAnalysisExtent = bUserCanDefineAnalysisExtent;

   }


   /**
    * Use this method to indicate that the algorithm is determinated (i.e. the number of steps to complete it is known)
    * 
    * @param bDeterminated
    *                true if it is a determinated algorithm
    */
   public void setIsDeterminatedProcess(final boolean bDeterminated) {

      m_bIsDeterminatedProcess = bDeterminated;

   }


   /**
    * 
    * @return true if the algorithm is determinated (i.e. the number of steps to complete it is known)
    */
   public boolean isDeterminatedProcess() {

      return m_bIsDeterminatedProcess;

   }


   //** Methods for creating and handling output objects (tables and layers) **/


   /**
    * @return the set of output objects generated by the algorithm
    */
   public OutputObjectsSet getOutputObjects() {

      return m_OutputObjects;

   }


   /**
    * sets a new set of output objects
    * 
    * @param ooSet
    *                the new set of output objects
    */
   public void setOutputObjects(final OutputObjectsSet ooSet) {

      m_OutputObjects = ooSet;

   }


   /**
    * Use this method to add a new output object. Use it if, for instance, you have a layer created not within the geoalgorithm
    * using a getNewXXXX method, but in a different place, like, for example, calling another method.
    * 
    * @param output
    *                The output object to add
    */
   protected void addOutputObject(final Output output) {

      m_OutputObjects.add(output);

   }


   /**
    * 
    * Use this method to add a new output object. Use it if, for instance, you have a layer created not within the geoalgorithm
    * using a getNewXXXX method, but in a different place, like, for example, calling another method.
    * 
    * @param sName
    *                the name of the output
    * @param sDescription
    *                the description (human-readable) of the output
    * @param channel
    *                the output channel to use in this output
    * @param obj
    *                the value of the output
    */
   protected void addOutputObject(final String sName,
                                  final String sDescription,
                                  final IOutputChannel channel,
                                  final Object obj) {
      Output out;
      if (obj instanceof IRasterLayer) {
         out = new OutputRasterLayer();
      }
      else if (obj instanceof IVectorLayer) {
         out = new OutputVectorLayer();
      }
      else if (obj instanceof ITable) {
         out = new OutputTable();
      }
      else if (obj instanceof String) {
         out = new OutputText();
      }
      else if (obj instanceof ChartPanel) {
         out = new OutputChart();
      }
      else {
         return;
      }

      out.setOutputChannel(channel);
      out.setDescription(sDescription);
      out.setName(sName);
      out.setOutputObject(obj);
      m_OutputObjects.add(out);

   }


   //------------ methods for creating layers and output objects-------------

   /**
    * Returns the output channel associated with the given output. If it is null, it returns a temporary output channel, which is
    * a {@link es.unex.sextante.dataObjects.FileOutputChannel} with a temporary filename. Returns null if no output with that name
    * exists.
    * 
    * @param sName
    *                the name of the output from which to retrieve the output channel
    * @return the output channel associated with the given output
    */
   protected IOutputChannel getOutputChannel(final String sName) {

      Output out;
      IOutputChannel channel;
      try {
         out = m_OutputObjects.getOutput(sName);
         channel = out.getOutputChannel();
         if (channel == null) {
            channel = new FileOutputChannel(m_OutputFactory.getTempFilename(out));
            out.setOutputChannel(channel);
            return channel;
         }
         else if (channel instanceof FileOutputChannel) {
            final String sFilename = getOutputFilename(out);
            if (sFilename == null) {
               ((FileOutputChannel) channel).setFilename(m_OutputFactory.getTempFilename(out));
               return channel;
            }
            else {
               ((FileOutputChannel) channel).setFilename(sFilename);
               return channel;
            }
         }
         else {
            return channel;
         }


      }
      catch (final Exception e) {
         e.printStackTrace();
         return null;
      }

   }


   /**
    * Returns the filename associated with a given output, which must have a file-based output channel. If the output filename
    * matches any of the valid extensions for the type of output (from the ones obtained from the current output factory), it
    * returns that filename. Otherwise, it adds the default extension for the type of output. If the filename is null, it returns
    * null.
    * 
    * @param out
    *                an output object
    * @return a checked filename for the passed output object. returns null if the output object filename is null or the output
    *         channel associated with the output is not file-based
    */
   protected String getOutputFilename(final Output out) {

      final IOutputChannel channel = out.getOutputChannel();

      if (!(channel instanceof FileOutputChannel)) {
         return null;
      }
      final String sFilename = ((FileOutputChannel) channel).getFilename();
      if (sFilename == null) {
         return null;
      }
      String[] exts = null;
      if (out instanceof OutputRasterLayer) {
         exts = m_OutputFactory.getRasterLayerOutputExtensions();
      }
      else if (out instanceof OutputVectorLayer) {
         exts = m_OutputFactory.getVectorLayerOutputExtensions();
      }
      else if (out instanceof OutputTable) {
         exts = m_OutputFactory.getTableOutputExtensions();
      }
      else if (out instanceof Output3DRasterLayer) {
         exts = m_OutputFactory.get3DRasterLayerOutputExtensions();
      }
      else {
         return null;
      }

      for (final String element : exts) {
         if (sFilename.endsWith(element)) {
            return sFilename;
         }
      }
      return sFilename + "." + exts[0];
   }


   /**
    * This method creates a new vector layer and adds it to the set of output objects of the algorithm. Use this when your
    * algorithm generates a new vector layer and you have to create it.
    * 
    * @param sName
    *                The name of the layer. Has to be the same that you used to define this output in the
    * @see {@link #defineCharacteristics()} method.
    * @param sDescription
    *                the long description of the output. This is the one usually used to describe the layer when added to a GIS
    *                GUI
    * @param iShapeType
    *                The shape type. See
    * @see {@link IVectorLayer} for more info about valid values
    * @param types
    *                an array of classes indicating data types for each field
    * @param sFields
    *                an array of field names
    * @return a new vector layer
    * @throws GeoAlgorithmExecutionException
    */
   protected IVectorLayer getNewVectorLayer(final String sName,
                                            final String sDescription,
                                            final int iShapeType,
                                            final Class[] types,
                                            final String[] sFields) throws GeoAlgorithmExecutionException {

      try {
         final OutputVectorLayer out = (OutputVectorLayer) m_OutputObjects.getOutput(sName);
         IVectorLayer layer;
         final IOutputChannel channel = getOutputChannel(sName);
         layer = m_OutputFactory.getNewVectorLayer(getLayerName(sName, sDescription), iShapeType, types, sFields, channel, m_CRS);
         addOutputVectorLayer(sName, sDescription, iShapeType, channel, layer, out.getInputLayerToOverwrite());
         return layer;
      }
      catch (final Exception e) {
         throw new GeoAlgorithmExecutionException(e.getMessage());
      }

   }


   /**
    * This method creates a new vector layer and adds it to the set of output objects of the algorithm. Use this when your
    * algorithm generates a new vector layer and you have to create it.
    * 
    * @param sName
    *                The name of the layer. Has to be the same that you used to define this output in the
    * @see {@link #defineCharacteristics()} method.
    * @param sDescription
    *                the long description of the output. This is the one usually used to describe the layer when added to a GIS
    *                GUI
    * @param iShapeType
    *                The shape type. See
    * @see {@link IVectorLayer} for more info about valid values
    * @param types
    *                an array of classes indicating data types for each field
    * @param sFields
    *                an array of field names
    * @param fieldSize
    *                An array of field sizes
    * @return a new vector layer
    * @throws GeoAlgorithmExecutionException
    */
   protected IVectorLayer getNewVectorLayer(final String sName,
                                            final String sDescription,
                                            final int iShapeType,
                                            final Class[] types,
                                            final String[] sFields,
                                            final int[] fieldSize) throws GeoAlgorithmExecutionException {

      try {
         final OutputVectorLayer out = (OutputVectorLayer) m_OutputObjects.getOutput(sName);
         IVectorLayer layer;
         final IOutputChannel channel = getOutputChannel(sName);
         layer = m_OutputFactory.getNewVectorLayer(getLayerName(sName, sDescription), iShapeType, types, sFields, channel, m_CRS,
                  fieldSize);
         addOutputVectorLayer(sName, sDescription, iShapeType, channel, layer, out.getInputLayerToOverwrite());
         return layer;
      }
      catch (final Exception e) {
         throw new GeoAlgorithmExecutionException(e.getMessage());
      }

   }


   private String getLayerName(final String sName,
                               final String sDescription) {

      if (m_OutputMap != null) {
         final String mappedName = m_OutputMap.get(sName);
         if (mappedName == null) {
            return sName;
         }
         else {
            return mappedName;
         }
      }
      else {
         return sDescription;
      }

   }


   /**
    * This method creates a new vector layer but does not add it to the set of output objects of the algorithm. If you are going
    * to read data from this layer, it is recommended to close the layer, call the postProcess() method and then open it again,
    * since some implementations might not support reading and writing features at the same time.
    * 
    * @param iShapeType
    *                The shape type. See
    * @see {@link IVectorLayer} for more info about valid values
    * @param types
    *                an array of classes indicating data types for each field
    * @param sFields
    *                an array of field names
    * @return a new vector layer
    * @throws UnsupportedOutputChannelException
    */
   protected IVectorLayer getTempVectorLayer(final int iShapeType,
                                             final Class[] types,
                                             final String[] sFields) throws UnsupportedOutputChannelException {

      final String sFilename = m_OutputFactory.getTempVectorLayerFilename();
      final IOutputChannel channel = new FileOutputChannel(sFilename);

      return m_OutputFactory.getNewVectorLayer("", iShapeType, types, sFields, channel, m_CRS);

   }


   /**
    * This method creates a new raster layer and adds it to the set of output objects of the algorithm. Use this when your
    * algorithm generates a new raster layer and you have to create it. The grid extent is taken from the algorithm analysis
    * extent (@see {@link #getAnalysisExtent()}
    * 
    * @param sName
    *                The name of the layer. Has to be the same that you used to define this output in the
    * @see {@link #defineCharacteristics()} method.
    * @param sDescription
    *                the long description of the output. This is the one usually used to describe the layer when added to a GIS
    *                GUI
    * @param iDataType
    *                The data type. See
    * @see {@link IRasterLayer} for more info about valid values
    * @param iBands
    *                the number of bands of the new layer
    * @return a new raster layer
    * @throws UnsupportedOutputChannelException
    */
   protected IRasterLayer getNewRasterLayer(final String sName,
                                            final String sDescription,
                                            final int iDataType,
                                            final int iBands) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final IRasterLayer newLayer = m_OutputFactory.getNewRasterLayer(getLayerName(sName, sDescription), iDataType,
               m_AnalysisExtent, iBands, channel, m_CRS);

      newLayer.setFullExtent();

      addOutputRasterLayer(sName, sDescription, iBands, channel, newLayer);

      return newLayer;

   }


   /**
    * This method creates a new monoband raster layer and adds it to the set of output objects of the algorithm. Use this when
    * your algorithm generates a new raster layer and you have to create it. The grid extent is taken from the algorithm analysis
    * extent (@see {@link #getAnalysisExtent()}
    * 
    * @param sName
    *                The name of the layer. Has to be the same that you used to define this output in the
    * @see {@link #defineCharacteristics()} method.
    * @param sDescription
    *                the long description of the output. This is the one usually used to describe the layer when added to a GIS
    *                GUI
    * @param iDataType
    *                The data type. See
    * @see {@link IRasterLayer} for more info about valid values
    * @return a new raster layer
    * @throws UnsupportedOutputChannelException
    */
   protected IRasterLayer getNewRasterLayer(final String sName,
                                            final String sDescription,
                                            final int iDataType) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final IRasterLayer newLayer = m_OutputFactory.getNewRasterLayer(getLayerName(sName, sDescription), iDataType,
               m_AnalysisExtent, 1, channel, m_CRS);

      newLayer.setFullExtent();

      addOutputRasterLayer(sName, sDescription, 1, channel, newLayer);

      return newLayer;

   }


   /**
    * This method creates a new raster layer and adds it to the set of output objects of the algorithm. Use this when your
    * algorithm generates a new raster layer and you have to create it.
    * 
    * @param sName
    *                The name of the layer. Has to be the same that you used to define this output in the
    * @see {@link #defineCharacteristics()} method.
    * @param sDescription
    *                the long description of the output. This is the one usually used to describe the layer when added to a GIS
    *                GUI
    * @param iDataType
    *                The data type. See
    * @see {@link IRasterLayer} for more info about valid values
    * @param extent
    *                The grid extent of the layer to create
    * @param iBands
    *                the number of bands of the new layer
    * @return a new raster layer
    * @throws UnsupportedOutputChannelException
    */
   protected IRasterLayer getNewRasterLayer(final String sName,
                                            final String sDescription,
                                            final int iDataType,
                                            final AnalysisExtent extent,
                                            final int iBands) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final IRasterLayer newLayer = m_OutputFactory.getNewRasterLayer(getLayerName(sName, sDescription), iDataType, extent,
               iBands, channel, m_CRS);

      newLayer.setFullExtent();

      addOutputRasterLayer(sName, sDescription, iBands, channel, newLayer);

      return newLayer;

   }


   /**
    * This method creates a new monoband raster layer and adds it to the set of output objects of the algorithm. Use this when
    * your algorithm generates a new raster layer and you have to create it.
    * 
    * @param sName
    *                The name of the layer. Has to be the same that you used to define this output in the
    * @see {@link #defineCharacteristics()} method.
    * @param sDescription
    *                the long description of the output. This is the one usually used to describe the layer when added to a GIS
    *                GUI
    * @param iDataType
    *                The data type. See
    * @see {@link IRasterLayer} for more info about valid values
    * @param extent
    *                The grid extent of the layer to create
    * @return a new raster layer
    * @throws UnsupportedOutputChannelException
    */
   protected IRasterLayer getNewRasterLayer(final String sName,
                                            final String sDescription,
                                            final int iDataType,
                                            final AnalysisExtent extent) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final IRasterLayer newLayer = m_OutputFactory.getNewRasterLayer(getLayerName(sName, sDescription), iDataType, extent, 1,
               channel, m_CRS);

      newLayer.setFullExtent();

      addOutputRasterLayer(sName, sDescription, 1, channel, newLayer);

      return newLayer;

   }


   protected IRasterLayer getTempRasterLayer(final int iDataType,
                                             final AnalysisExtent extent,
                                             final int iBands) throws UnsupportedOutputChannelException {

      final String sFilename = m_OutputFactory.getTempRasterLayerFilename();
      final IOutputChannel channel = new FileOutputChannel(sFilename);

      final IRasterLayer newLayer = m_OutputFactory.getNewRasterLayer("", iDataType, extent, iBands, channel, m_CRS);

      newLayer.setFullExtent();

      return newLayer;

   }


   protected IRasterLayer getTempRasterLayer(final int iDataType,
                                             final AnalysisExtent extent) throws UnsupportedOutputChannelException {

      final String sFilename = m_OutputFactory.getTempRasterLayerFilename();
      final IOutputChannel channel = new FileOutputChannel(sFilename);

      final IRasterLayer newLayer = m_OutputFactory.getNewRasterLayer("", iDataType, extent, 1, channel, m_CRS);

      newLayer.setFullExtent();

      return newLayer;

   }


   protected I3DRasterLayer getNew3DRasterLayer(final String sName,
                                                final String sDescription,
                                                final int iDataType,
                                                final AnalysisExtent extent) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final I3DRasterLayer newLayer = m_OutputFactory.getNew3DRasterLayer(getLayerName(sName, sDescription), iDataType, extent,
               channel, m_CRS);

      addOutput3DRasterLayer(sName, sDescription, channel, newLayer);

      return newLayer;

   }


   protected I3DRasterLayer getNew3DRasterLayer(final String sName,
                                                final String sDescription,
                                                final int iDataType) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final I3DRasterLayer newLayer = m_OutputFactory.getNew3DRasterLayer(getLayerName(sName, sDescription), iDataType,
               m_AnalysisExtent, channel, m_CRS);

      addOutput3DRasterLayer(sName, sDescription, channel, newLayer);

      return newLayer;

   }


   protected ITable getNewTable(final String sName,
                                final String sDescription,
                                final Class[] types,
                                final String[] sFields) throws UnsupportedOutputChannelException {

      final IOutputChannel channel = getOutputChannel(sName);

      final ITable table = m_OutputFactory.getNewTable(getLayerName(sName, sDescription), types, sFields, channel);

      addOutputTable(sName, sDescription, channel, table);

      return table;

   }


   /////////////////////////////////////////////////////////////////////////////////////

   /**
    * Adds a new raster layer to the output set
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param iBands
    *                The number of bands the output layer will have
    * @param channel
    *                the output channel associated to the output
    * @param layer
    *                the layer itself
    */
   protected void addOutputRasterLayer(final String sName,
                                       final String sDescription,
                                       final int iBands,
                                       final IOutputChannel channel,
                                       final IRasterLayer layer) {

      final OutputRasterLayer out = new OutputRasterLayer();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(layer);
      out.setOutputChannel(channel);
      out.setNumberOfBands(iBands);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new raster layer to the output set. The value of the output is null, and so is the filename. Use this method in the
    * DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param iBands
    *                The number of bands the output layer will have
    */
   protected void addOutputRasterLayer(final String sName,
                                       final String sDescription,
                                       final int iBands) {

      addOutputRasterLayer(sName, sDescription, iBands, null, null);

   }


   /**
    * Adds a new monoband raster layer to the output set. The value of the output is null, and so is the filename. Use this method
    * in the DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    */
   protected void addOutputRasterLayer(final String sName,
                                       final String sDescription) {

      addOutputRasterLayer(sName, sDescription, 1, null, null);

   }


   /**
    * Adds a new 3D raster layer to the output set. The value of the output is null, and so is the filename. Use this method in
    * the DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    */

   protected void addOutput3DRasterLayer(final String sName,
                                         final String sDescription) {

      addOutput3DRasterLayer(sName, sDescription, null, null);

   }


   /**
    * Adds a new 3D raster layer to the output set
    * 
    * @param sName
    *                the name to identify the layer in the set
    * @param sDescription
    *                the description of the layer
    * @param channel
    *                the output channel associated to the output
    * @param layer
    *                the layer itself
    */
   protected void addOutput3DRasterLayer(final String sName,
                                         final String sDescription,
                                         final IOutputChannel channel,
                                         final I3DRasterLayer layer) {

      final Output3DRasterLayer out = new Output3DRasterLayer();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(layer);
      out.setOutputChannel(channel);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new vector layer to the output set
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param iType
    *                the shape type of the layer
    * @param channel
    *                the output channel associated to the output
    * @param layer
    *                the layer itself
    * @param sInputLayerToOverwrite
    *                the name of the input param (another vector layer) that this output can overwrite
    * 
    */
   protected void addOutputVectorLayer(final String sName,
                                       final String sDescription,
                                       final int iType,
                                       final IOutputChannel channel,
                                       final IVectorLayer layer,
                                       final String sInputLayerToOverwrite) {

      final OutputVectorLayer out = new OutputVectorLayer();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(layer);
      out.setOutputChannel(channel);
      out.setShapeType(iType);
      out.setInputLayerToOverwrite(sInputLayerToOverwrite);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new vector layer to the output set. The value of the output is null, and so is the filename. Use this method in the
    * DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param iType
    *                the shape type of the layer
    * @param sInputLayerToOverwrite
    *                the name of the input param (another vector layer) that this output can overwrite
    */
   protected void addOutputVectorLayer(final String sName,
                                       final String sDescription,
                                       final int iType,
                                       final String sInputLayerToOverwrite) {

      addOutputVectorLayer(sName, sDescription, iType, null, null, sInputLayerToOverwrite);

   }


   /**
    * Adds a new vector layer to the output set. The value of the output is null, and so is the filename. Use this method in the
    * DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param iType
    *                the shape type of the layer
    */
   protected void addOutputVectorLayer(final String sName,
                                       final String sDescription,
                                       final int iType) {

      addOutputVectorLayer(sName, sDescription, iType, null, null, null);

   }


   /**
    * Adds a new vector layer to the output set. The value of the output is null, and so is the filename. Use this method in the
    * DefineCharacteristics() method of the algorithm, to define the outputs expected. The shape type is set to undefined
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    */
   protected void addOutputVectorLayer(final String sName,
                                       final String sDescription) {

      addOutputVectorLayer(sName, sDescription, OutputVectorLayer.SHAPE_TYPE_UNDEFINED, null, null, null);

   }


   /**
    * Adds a new table to the output set
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param channel
    *                the output channel associated to the output
    * @param table
    *                the table itself
    */
   protected void addOutputTable(final String sName,
                                 final String sDescription,
                                 final IOutputChannel channel,
                                 final ITable table) {

      final Output out = new OutputTable();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(table);
      out.setOutputChannel(channel);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new table to the output set. The value of the output is null, and so is the filename. Use this method in the
    * DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    */
   protected void addOutputTable(final String sName,
                                 final String sDescription) {

      addOutputTable(sName, sDescription, null, null);

   }


   /**
    * Adds a new chart panel to the output set
    * 
    * @param sName
    *                The name to identify the layer in the set
    * @param sDescription
    *                The description of the layer
    * @param chart
    *                a chart panel
    */
   protected void addOutputChart(final String sName,
                                 final String sDescription,
                                 final ChartPanel chart) {

      final Output out = new OutputChart();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(chart);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new chart to the output set. The value of the output is null. Use this method in the DefineCharacteristics() method
    * of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the output in the set
    * @param sDescription
    *                The description of the output
    */
   protected void addOutputChart(final String sName,
                                 final String sDescription) {

      addOutputChart(sName, sDescription, null);

   }


   /**
    * Adds a new image to the output set. The value of the output is null. Use this method in the DefineCharacteristics() method
    * of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the output in the set
    * @param sDescription
    *                The description of the output
    */
   protected void addOutputImage(final String sName,
                                 final String sDescription) {

      final Output out = new OutputImage();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(null);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new text string to the output set
    * 
    * @param sName
    *                The name to identify the string in the set
    * @param sDescription
    *                The description of the string
    * @param sText
    *                the text to add (HTML formatted text)
    */
   protected void addOutputText(final String sName,
                                final String sDescription,
                                final String sText) {

      final Output out = new OutputText();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(sText);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new text numerical value to the output set. The value of the output is null. Use this method in the
    * DefineCharacteristics() method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the output in the set
    * @param sDescription
    *                The description of the output
    */
   protected void addOutputNumericalValue(final String sName,
                                          final String sDescription) {

      final Output out = new OutputNumericalValue();
      out.setName(sName);
      out.setDescription(sDescription);
      out.setOutputObject(null);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new numerical value to the output set
    * 
    * @param sName
    *                The name to identify the value in the set
    * @param number
    *                the value of the output
    */
   protected void addOutputNumericalValue(final String sName,
                                          final Number number) {

      final Output out = new OutputNumericalValue();
      out.setName(sName);
      out.setDescription(sName);
      out.setOutputObject(number);

      m_OutputObjects.add(out);

   }


   /**
    * Adds a new text string to the output set. The value of the output is null. Use this method in the DefineCharacteristics()
    * method of the algorithm, to define the outputs expected.
    * 
    * @param sName
    *                The name to identify the output in the set
    * @param sDescription
    *                The description of the output
    */
   protected void addOutputText(final String sName,
                                final String sDescription) {

      addOutputText(sName, sDescription, null);

   }


   @Override
   public String toString() {

      return m_sName;

   }


   /**
    * 
    * @return the command line name of the algorithm
    */
   public String getCommandLineName() {

      final String sClass = this.getClass().getName();
      final int iLast = sClass.lastIndexOf(".");
      final String sCommandName = sClass.substring(iLast + 1, sClass.length() - "Algorithm".length());

      return sCommandName.toLowerCase();

   }


   /**
    * 
    * @return a new instance of the algorithm
    * @throws InstantiationException
    * @throws IllegalAccessException
    */
   public GeoAlgorithm getNewInstance() throws InstantiationException, IllegalAccessException {

      final GeoAlgorithm alg = this.getClass().newInstance();

      alg.setName(m_sName);
      alg.setGroup(m_sGroup);

      alg.setParameters(m_Parameters.getNewInstance());
      alg.setOutputObjects(m_OutputObjects.getNewInstance());

      if (m_bIsAutoExtent) {
         alg.setAnalysisExtent(null);
         alg.m_bIsAutoExtent = true;
      }

      return alg;

   }


   /**
    * 
    * @return true if the algorithm is suitable for a modeling process. This should be based on how well-defined the outputs are.
    *         If, for instance, the number of output layers cannot be known in advance, then this method should return false
    */
   public boolean isSuitableForModelling() {

      return true;

   }


   /**
    * Any preprocessing needed before executing the algorithm as part of a model should be carried on in this method. Also, this
    * method should be used to do any extra checking, to ensure that parameter values are correct. If not, an exception should be
    * thrown. If data are not yet available (another algorithm in the model has to be run before this one), this method should
    * return false. Otherwise, should return true if the algorithm can be run after being preprocessed.
    * 
    * @param model
    *                the model from which the algorithm is being executed
    * @throws GeoAlgorithmExecutionException
    *                 if the parameters are not correct and the algorithm cannot be executed
    * @returns true if the algorithm can be executed after being preprocessed
    */
   public boolean preprocessForModeller(final Object model) throws GeoAlgorithmExecutionException {

      //By default it does nothing, since most algorithms
      //do not need preprocessing or checking at all.

      return true;

   }


   /**
    * Returns true if the algorithms generates new layers
    * 
    * @return true if the algorithms generates new layers
    */
   public boolean generatesLayers() {

      return m_OutputObjects.hasLayers();

   }


   /**
    * Returns true if the algorithms generates new layers or tables
    * 
    * @return true if the algorithms generates new layers or tables
    */
   public boolean generatesLayersOrTables() {

      return m_OutputObjects.hasDataObjects();

   }


   /**
    * Returns the number of new objects (layers and tables) generated by the algorithm
    * 
    * @return the number of new objects (layers and tables) generated by the algorithm
    */
   public int getNumberOfOutputObjects() {

      return m_OutputObjects.getOutputDataObjectsCount();

   }


   /**
    * Returns a string containing the command line usage of the algorithm
    * 
    * @return a string containing the command line usage of the algorithm
    */
   public String getCommandLineHelp() {

      final String sFirstLine = "Usage: runalg( \"" + getCommandLineName() + "\",\n";

      return getCommandLineParametersHelp(sFirstLine);

   }


   protected String getCommandLineParametersHelp(final String sFirstLine) {

      int i;
      final ParametersSet params = getParameters();
      final StringBuffer sb = new StringBuffer(sFirstLine);
      sb.append(getfixedLengthBlankLine(sFirstLine.length()));

      for (i = 0; i < params.getNumberOfParameters(); i++) {
         final Parameter param = params.getParameter(i);
         sb.append(param.getParameterName() + "[");
         if (param instanceof ParameterDataObject) {
            AdditionalInfoDataObject ai;
            try {
               ai = (AdditionalInfoDataObject) param.getParameterAdditionalInfo();
               if (!ai.getIsMandatory()) {
                  sb.append("Optional ");
               }
            }
            catch (final NullParameterAdditionalInfoException e) {}

         }
         sb.append(param.getParameterTypeName());
         if (param instanceof ParameterTableField) {
            AdditionalInfoTableField ai;
            try {
               ai = (AdditionalInfoTableField) param.getParameterAdditionalInfo();
               sb.append(" from " + ai.getParentParameterName());
            }
            catch (final NullParameterAdditionalInfoException e) {}
         }
         if (param instanceof ParameterFixedTable) {
            AdditionalInfoFixedTable ai;
            try {
               ai = (AdditionalInfoFixedTable) param.getParameterAdditionalInfo();
               final String sCols[] = ai.getCols();
               sb.append(" (Cols:");
               for (final String element : sCols) {
                  sb.append(" | " + element);
               }
               sb.append(" | ). (Rows: ");
               if (ai.isNumberOfRowsFixed()) {
                  sb.append(ai.getRowsCount() + ")");
               }
               else {
                  sb.append("any)");
               }
            }
            catch (final NullParameterAdditionalInfoException e) {}
         }
         else if (param instanceof ParameterMultipleInput) {
            AdditionalInfoMultipleInput ai;
            try {
               ai = (AdditionalInfoMultipleInput) param.getParameterAdditionalInfo();
               switch (ai.getDataType()) {
                  case AdditionalInfoMultipleInput.DATA_TYPE_BAND:
                     sb.append(" - Band");
                     break;
                  case AdditionalInfoMultipleInput.DATA_TYPE_RASTER:
                     sb.append(" - Raster Layer");
                     break;
                  case AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_ANY:
                     sb.append(" - Vector Layer");
                     break;
                  case AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_POINT:
                     sb.append(" - Points Layer");
                     break;
                  case AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_LINE:
                     sb.append(" - Lines Layer");
                     break;
                  case AdditionalInfoMultipleInput.DATA_TYPE_VECTOR_POLYGON:
                     sb.append(" - Polygons Layer");
                     break;
                  case AdditionalInfoMultipleInput.DATA_TYPE_TABLE:
                     sb.append(" - Table");
                     break;
               }
            }
            catch (final NullParameterAdditionalInfoException e) {}
         }
         sb.append("]");
         sb.append(",\n" + getfixedLengthBlankLine(sFirstLine.length()));
      }

      for (i = 0; i < m_OutputObjects.getOutputObjectsCount(); i++) {
         final Output out = m_OutputObjects.getOutput(i);
         if ((out instanceof OutputRasterLayer) || (out instanceof OutputVectorLayer) || (out instanceof OutputTable)) {
            //i++;
            sb.append(out.getName() + "[output ");
            if (out instanceof OutputRasterLayer) {
               sb.append("raster layer");
            }
            else if (out instanceof OutputVectorLayer) {
               sb.append("vector layer");
            }
            else if (out instanceof OutputTable) {
               sb.append("table");
            }
            sb.append("]");
            sb.append(",\n" + getfixedLengthBlankLine(sFirstLine.length()));
         }
      }

      sb.append(");");

      return sb.toString();

   }


   private String getfixedLengthBlankLine(final int iLength) {

      final String sResult = "                                                                     ";

      return sResult.substring(0, iLength);

   }


   /**
    * Returns the command line expression that would create this instance of the algorithm
    * 
    * @return A command line expression representing this algorithm
    */
   public String[] getAlgorithmAsCommandLineSentences() {

      int i;
      String[] sReturn;
      final String sFirstLine = "runalg(\"" + getCommandLineName() + "\", ";
      final StringBuffer sb = new StringBuffer(sFirstLine);

      for (i = 0; i < m_Parameters.getNumberOfParameters(); i++) {
         final Parameter param = m_Parameters.getParameter(i);
         sb.append(param.getCommandLineParameter() + ", ");
      }

      for (int j = 0; j < m_OutputObjects.getOutputObjectsCount(); j++) {
         final Output out = m_OutputObjects.getOutput(j);
         if ((out instanceof OutputRasterLayer) || (out instanceof Output3DRasterLayer) || (out instanceof OutputVectorLayer)
             || (out instanceof OutputTable)) {
            sb.append(out.getCommandLineParameter() + ", ");
         }
      }

      final String sAlg = sb.substring(0, sb.length() - 2) + ")";

      if (getUserCanDefineAnalysisExtent()) {
         sReturn = new String[2];
         if (m_bIsAutoExtent) {
            sReturn[0] = "autoextent(\"true\")";
         }
         else {
            final AnalysisExtent ge = getAnalysisExtent();
            sReturn[0] = "extent( " + ge.toString() + ")";
         }
         sReturn[1] = sAlg;
      }
      else {
         sReturn = new String[1];
         sReturn[0] = sAlg;
      }

      return sReturn;

   }


   ////////////////////////Requirements/////////////////////////////

   /**
    * Returns true if the algorithm requires vector layers to run
    * 
    * @see ParametersSet#requiresVectorLayers()
    * @return true if the algorithm requires vector layers to run
    */
   public boolean requiresVectorLayers() {

      return m_Parameters.requiresVectorLayers();

   }


   /**
    * Returns true if the algorithm requires vector layers as individual input (i.e. not as a multiple input)
    * 
    * @return true if the algorithm requires vector layers as individual input
    */
   public boolean requiresIndividualVectorLayers() {

      return m_Parameters.getNumberOfVectorLayers() > 0;

   }


   /**
    * Returns true if the algorithm requires polygon vector layers to run
    * 
    * @see ParametersSet#requiresPolygonVectorLayers()
    * @return true if the algorithm requires polygon vector layers to run
    */
   public boolean requiresPolygonVectorLayers() {

      return m_Parameters.requiresPolygonVectorLayers();

   }


   /**
    * Returns true if the algorithm requires line vector layers to run
    * 
    * @see ParametersSet#requiresLineVectorLayers()
    * @return true if the algorithm requires line vector layers to run
    */
   public boolean requiresLineVectorLayers() {

      return m_Parameters.requiresLineVectorLayers();

   }


   /**
    * Returns true if the algorithm requires point vector layers to run
    * 
    * @see ParametersSet#requiresVectorLayers()
    * @return true if the algorithm requires point vector layers to run
    */
   public boolean requiresPointVectorLayers() {

      return m_Parameters.requiresPointVectorLayers();

   }


   /**
    * Returns true if the algorithm requires raster layers to run
    * 
    * @see ParametersSet#requiresVectorLayers()
    * @return true if the algorithm requires raster layers to run
    */
   public boolean requiresRasterLayers() {

      return m_Parameters.requiresRasterLayers();

   }


   /**
    * Returns true if the algorithm requires 3D raster layers to run
    * 
    * @see ParametersSet#requiresVectorLayers()
    * @return true if the algorithm requires raster layers to run
    */
   public boolean requires3DRasterLayers() {

      return m_Parameters.requires3DRasterLayers();

   }


   /**
    * Returns true if the algorithm requires raster layers as individual input (i.e. not as a multiple input)
    * 
    * @return true if the algorithm requires raster layers as individual input
    */
   public boolean requiresIndividualRasterLayers() {

      return m_Parameters.getNumberOfRasterLayers() > 0;

   }


   /**
    * Returns true if the algorithm requires raster layers as multiple input
    * 
    * @return true if the algorithm requires raster layers as multiple input
    */
   public boolean requiresMultipleRasterLayers() {

      return m_Parameters.requiresMultipleRasterLayers();

   }


   /**
    * Returns true if the algorithm requires vector layers as multiple input
    * 
    * @return true if the algorithm requires vector layers as multiple input
    */
   public boolean requiresMultipleVectorLayers() {

      return m_Parameters.requiresMultipleVectorLayers();

   }


   /**
    * Returns true if the algorithm requires tables layers as multiple input
    * 
    * @return true if the algorithm requires tables layers as multiple input
    */
   public boolean requiresMultipleTables() {

      return m_Parameters.requiresMultipleTables();

   }


   /**
    * Returns true if the algorithm requires raster bands as multiple input (i.e. the parameter set contains a multiple input
    * parameter of type band)
    * 
    * @return true if the algorithm requires raster bands as multiple input
    */
   public boolean requiresMultipleRasterBands() {

      return m_Parameters.requiresMultipleRasterBands();

   }


   /**
    * Returns true if the algorithm requires raster layers to run
    * 
    * @see ParametersSet#requiresVectorLayers()
    * @return true if the algorithm requires raster layers to run
    */
   public boolean requiresTables() {

      return m_Parameters.requiresTables();

   }


   /**
    * Returns true if the algorithm requires table field layers to run
    * 
    * @see ParametersSet#requiresTableFields()
    * @return true if the algorithm requires table fields to run
    */
   public boolean requiresTableFields() {

      return m_Parameters.requiresTableFields();

   }


   /**
    * Returns true if the algorithm requires point coordinates to run
    * 
    * @see ParametersSet#requiresPoints()
    * @return true if the algorithm requires point coordinates to run
    */
   public boolean requiresPoints() {

      return m_Parameters.requiresPoints();

   }


   /**
    * Returns false if there are not enough raster data in the given array to run the algorithm
    * 
    * @param objs
    *                an array of available data objects
    * @return false if the algorithm requires more raster layers than the ones in the data objects array. True if they suffice
    */
   public boolean meetsRasterRequirements(final Object[] objs) {

      if (objs == null) {
         return false;
      }

      if (requiresRasterLayers()) {
         for (final Object element : objs) {
            if (element instanceof IRasterLayer) {
               return true;
            }
         }
         return false;
      }
      else {
         return true;
      }

   }


   /**
    * Returns false if there are not enough 3D raster data in the given array to run the algorithm
    * 
    * @param objs
    *                an array of available data objects
    * @return false if the algorithm requires more raster layers than the ones in the data objects array. True if they suffice
    */
   public boolean meets3DRasterRequirements(final Object[] objs) {

      if (objs == null) {
         return false;
      }

      if (requires3DRasterLayers()) {
         for (final Object element : objs) {
            if (element instanceof I3DRasterLayer) {
               return true;
            }
         }
         return false;
      }
      else {
         return true;
      }

   }


   /**
    * Returns false if there are not enough vector data in the given array to run the algorithm
    * 
    * @param objs
    *                an array of available data objects
    * @return false if the algorithm requires more (or different, i.e other shape type) vector layers than the ones in the data
    *         objects array. True if they suffice
    */
   public boolean meetsVectorRequirements(final Object[] objs) {

      boolean bPolygonLayers = false;
      boolean bLineLayers = false;
      boolean bPointLayers = false;
      boolean bVectorLayers = false;

      if (objs == null) {
         return false;
      }

      if (requiresVectorLayers()) {
         for (final Object element : objs) {
            if (element instanceof IVectorLayer) {
               switch (((IVectorLayer) element).getShapeType()) {
                  case IVectorLayer.SHAPE_TYPE_POINT:
                     bPointLayers = true;
                     bVectorLayers = true;
                     break;
                  case IVectorLayer.SHAPE_TYPE_LINE:
                     bLineLayers = true;
                     bVectorLayers = true;
                     break;
                  case IVectorLayer.SHAPE_TYPE_POLYGON:
                     bPolygonLayers = true;
                     bVectorLayers = true;
                     break;
               }
            }
         }

         if ((requiresPointVectorLayers() && !bPointLayers) || (requiresLineVectorLayers() && !bLineLayers)
             || (requiresPolygonVectorLayers() && !bPolygonLayers)) {
            return false;
         }
         else {
            return bVectorLayers;
         }
      }
      else {
         return true;

      }


   }


   /**
    * Returns false if there are not enough table objects in the given array to run the algorithm
    * 
    * @param objs
    *                an array of available data objects
    * @return false if the algorithm requires more tables than the ones in the data objects array. True if they suffice
    */
   public boolean meetsTableRequirements(final Object[] objs) {

      if (objs == null) {
         return false;
      }

      if (requiresTables()) {
         for (final Object element : objs) {
            if (element instanceof ITable) {
               return true;
            }
         }
         return false;
      }
      else {
         return true;
      }

   }


   /**
    * Returns true if the algorithm could be executed with the given data objects.
    * 
    * @param objs
    *                an array of available data objects
    * @return true if the algorithm could be executed with the given data objects.
    */
   public boolean meetsDataRequirements(final Object[] objs) {

      if (objs == null) {
         return false;
      }

      return meets3DRasterRequirements(objs) && meetsRasterRequirements(objs) && meetsVectorRequirements(objs)
             && meetsTableRequirements(objs);

   }


   /**
    * Returns true if the algorithm parameters have correct values.
    * 
    * @return true if the algorithm parameters have correct values
    */
   public boolean hasCorrectParameterValues() {

      return m_Parameters.areParameterValuesCorrect();
   }


   /**
    * Returns true if the extent has been set automatically by the algorithm, false if it was introduced by the user
    * 
    * @return true if the extent has been set automatically by the algorithm, false if it was introduced by the user
    */
   public boolean isAutoExtent() {
      return m_bIsAutoExtent;
   }


   /**
    * Return true if the algorithm can define the output extent from its required input layers. False otherwise.
    * 
    * @return true if the algorithm can define the output extent from its required input layers. False otherwise.
    */
   public boolean canDefineOutputExtentFromInput() {

      if (m_OutputObjects.get3DRasterLayersCount() != 0) {
         return requires3DRasterLayers();
      }

      if (m_OutputObjects.getRasterLayersCount() != 0) {
         return requiresRasterLayers() || requires3DRasterLayers();
      }

      if (m_OutputObjects.getVectorLayersCount() != 0) {
         return requiresRasterLayers() || requires3DRasterLayers() || requiresVectorLayers();
      }

      return true;

   }


   /**
    * Until we find a way of automating this, 3d algorithms should overwrite this method to return true. Otherwise, it will return
    * false by default. This method is used to know whether to show a 3D Analysis Extent definition panel in the algorithm dialog
    * or a regular 2D one
    * 
    * @return true if the algorithm takes 3D inputs or produces 3D output
    */
   public boolean is3D() {

      return false;

   }


   /**
    * returns the crs used by the algorithm to produces its outputs
    * 
    * @return the crs used by the algorithm to produces its outputs;
    */
   public Object getOutputCRS() {

      return m_CRS;

   }


   /**
    * Returns true if the algorithm should be shown in a SEXTANTE component, false otherwise. Use this to programatically hide
    * algorithms if you do not want them to be shown and used for some reason.
    * 
    * @return true if the algorithm should be shown in a SEXTANTE component, false otherwise.
    */
   public boolean isActive() {

      return true;

   }

}
