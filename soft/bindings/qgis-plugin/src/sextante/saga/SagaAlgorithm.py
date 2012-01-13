from sextante.core.GeoAlgorithm import GeoAlgorithm
from sextante.saga.SagaBlackList import SagaBlackList
from sextante.saga.UnwrappableSagaAlgorithmException import UnwrappableSagaAlgorithmException
from sextante.parameters.ParameterTable import ParameterTable
from sextante.parameters.ParameterFixedTable import ParameterFixedTable
from sextante.parameters.ParameterTableField import ParameterTableField
from sextante.outputs.OutputTable import OutputTable
from sextante.parameters.ParameterMultipleInput import ParameterMultipleInput
from sextante.parameters.ParameterRaster import ParameterRaster
from sextante.outputs.OutputRaster import OutputRaster
from sextante.parameters.ParameterVector import ParameterVector
from sextante.parameters.ParameterNumber import ParameterNumber
from sextante.parameters.ParameterBoolean import ParameterBoolean
from sextante.parameters.ParameterString import ParameterString
from sextante.parameters.ParameterSelection import ParameterSelection
from sextante.core.SextanteUtils import SextanteUtils
from sextante.saga.SagaExecutionException import SagaExecutionException
import os
from sextante.outputs.OutputVector import OutputVector
from sextante.saga.SagaUtils import SagaUtils
import time
from sextante.saga.SagaGroupNameDecorator import SagaGroupNameDecorator




class SagaAlgorithm(GeoAlgorithm):

    def __init__(self, descriptionfile):
        GeoAlgorithm.__init__(self)
        self._descriptionFile = descriptionfile
        self.defineCharacteristicsFromFile()
        self.numExportedLayers = 0
        self.providerName = "saga:"

    def defineCharacteristics(self):
        pass

    def defineCharacteristicsFromFile(self):

        lines = open(self._descriptionFile)
        line = lines.readline()
        while line != "":
            line = line.strip("\n").strip()
            readLine = True;
            #line = lines.readline()
            if line.startswith("library name"):
                self.undecoratedGroup = line.split("\t")[1]
                self.group = SagaGroupNameDecorator.getDecoratedName(self.undecoratedGroup)

            if line.startswith("module name"):
                self.name = line.split("\t")[1]
            if line.startswith("-"):
                try:
                    paramName = line[1:line.index(":")]
                    paramDescription = line[line.index(">") + 1:].strip()
                except Exception: #boolean params have a different syntax
                    paramName = line[1:line.index("\t")]
                    paramDescription = line[line.index("\t") + 1:].strip()
                if SagaBlackList.isBlackListed(self.name, self.group):
                    raise UnwrappableSagaAlgorithmException()
                line = lines.readline()
                if "Data object" in line or "File" in line:
                    raise UnwrappableSagaAlgorithmException()
                if "Table" in line:
                    if "input" in line:
                        param = ParameterTable()
                        param.name = paramName
                        param.description = paramDescription
                        param.optional = not ("optional" in line)
                        lastParentParameterName = paramName;
                        self.putParameter(param)
                    elif "Static" in line:
                        line = lines.readline()
                        line = lines.readline().strip("\n").strip()
                        numCols = int(line.split(" ")[0])
                        colNames = [];
                        for i in range(numCols):
                            line = lines.readline().strip("\n").strip()
                            colNames.append(line.split("]")[1])
                        param = ParameterFixedTable()
                        param.name = paramName
                        param.description = paramDescription
                        param.cols = colNames
                        param.numRows = 3
                        param.fixedNumOfRows = False
                        self.putParameter(param)
                    elif "field" in line:
                        if lastParentParameterName == None:
                            raise UnwrappableSagaAlgorithmException();
                        param = ParameterTableField()
                        param.name = paramName
                        param.description = paramDescription
                        param.parent = lastParentParameterName
                        self.putParameter(param)
                    else:
                        output = OutputTable()
                        output.name = paramName
                        output.description = paramDescription
                if "Grid" in line:
                    if "input" in line:
                        if "list" in line:
                            param = ParameterMultipleInput()
                            param.name = paramName
                            param.description = paramDescription
                            param.optional = not ("optional" in line)
                            param.datatype=ParameterMultipleInput.TYPE_RASTER
                            self.putParameter(param)
                        else:
                            param = ParameterRaster()
                            param.name = paramName
                            param.description = paramDescription
                            param.optional = not ("optional" in line)
                            self.putParameter(param)
                    else:
                        output = OutputRaster()
                        output.name = paramName
                        output.description = paramDescription
                        self.putOutput(output)
                elif "Shapes" in line:
                    if "input" in line:
                        if "list" in line:
                            param = ParameterMultipleInput()
                            param.name = paramName
                            param.description = paramDescription
                            param.optional = not ("optional" in line)
                            param.datatype=ParameterMultipleInput.TYPE_VECTOR_ANY
                            self.putParameter(param)
                        else:
                            param = ParameterVector()
                            param.name = paramName
                            param.description = paramDescription
                            param.optional = not ("optional" in line)
                            param.shapetype = ParameterVector.VECTOR_TYPE_ANY
                            lastParentParameterName = paramName;
                            self.putParameter(param)
                    else:
                        output = OutputRaster()
                        output.name = paramName
                        output.description = paramDescription
                        self.putOutput(output)
                elif "Floating" in line or "Integer" in line:
                    param = ParameterNumber()
                    param.name = paramName
                    param.description = paramDescription
                    self.putParameter(param)
                elif "Boolean" in line:
                    param = ParameterBoolean()
                    param.name = paramName
                    param.description = paramDescription
                    self.putParameter(param)
                elif "Text" in line:
                    param = ParameterString()
                    param.name = paramName
                    param.description = paramDescription
                    self.putParameter(param)
                elif "Choice" in line:
                    line = lines.readline()
                    line = lines.readline().strip("\n").strip()
                    options = list()
                    while line != "" and not (line.startswith("-")):
                        options.append(line);
                        line = lines.readline().strip("\n").strip()
                    param = ParameterSelection()
                    param.name = paramName
                    param.description = paramDescription
                    param.options = options
                    self.putParameter(param)
                    if line == "":
                        break
                    else:
                        readLine = False
            if readLine:
                line = lines.readline()
        lines.close()

    def proccessAlgorithm(self):

        commands = list()
        self.exportedLayers = {}
        self.numExportedLayers = 0;

        #resolve temporary output files
        for out in self.outputs:
            if out.channel == None:
                SextanteUtils.setTempOutput(out)

      #1: Export rasters to sgrd. only ASC and TIF are supported.
      #   Vector layers must be in shapefile format and tables in dbf format. We check that.
        for param in self.parameters.values:
            if isinstance(param, ParameterRaster):
                if param.value == None:
                    continue;
                if not param.value.endswith("sgrd"):
                    commands.append(self.exportRasterLayer(param.value));
            if isinstance(param, ParameterVector):
                if param.value == None:
                    continue;
                if not param.value.endswith("shp"):
                    raise SagaExecutionException("Unsupported file format")
            if isinstance(param, ParameterTable):
                if param.value == None:
                    continue;
                if not param.value.endswith("dbf"):
                    raise SagaExecutionException("Unsupported file format")
            if isinstance(param, ParameterMultipleInput):
                layers = param.value
                if layers == None or len(layers) == 0:
                    continue
                if param.datatype == ParameterMultipleInput.TYPE_RASTER:
                    for layer in layers:
                        commands.append(self.exportRasterLayer(layer))
                elif param.datatype == ParameterMultipleInput.TYPE_VECTOR_ANY:
                    for layer in layers:
                        if not layer.endswith("shp"):
                            raise SagaExecutionException("Unsupported file format")

        #2: set parameters and outputs
        command = self.undecoratedGroup  + " \"" + self.name + "\""
        for param in self.parameters.values:
            if isinstance(param, ParameterRaster):
                if param.value == None:
                    continue;
                if param.value in self.exportedLayers.keys():
                    command+=(" -" + param.name + " " + self.exportedLayers[param.value])
                else:
                    command+=(" -" + param.name + " " + param.value)
            if isinstance(param, ParameterVector) or isinstance(param, ParameterTable):
                if param.value == None:
                    continue;
                command+=(" -" + param.name + " " + param.value)
            elif isinstance(param, ParameterMultipleInput):
                if param.value == None:
                    continue;
                if len(param.value) == 0:
                    continue;
                command +=(" -" + param.name + " ");
                layers = param.value
                if param.datatype == ParameterMultipleInput.TYPE_RASTER:
                    for layer in layers:
                        commands+=(self.exportRasterLayer(layer))
                        if layer != layers[-1]:
                            commands+=";"
                elif param.datatype == ParameterMultipleInput.TYPE_VECTOR_ANY:
                    for layer in layers:
                        commands.add(layer)
                        if layer != layers[-1]:
                            commands+=";"
            elif isinstance(param, ParameterBoolean):
                if (param):
                    command+=(" -" + param.name);
            else:
                command+=(" -" + param.name + " " + str(param.value));


        for out in self.outputs.values:
            if isinstance(out, OutputRaster):
                filename = out.value
                if not filename.endswith(".sgrd"):
                    filename += ".sgrd"
                    out.value = filename
                #A LO MEJOR NO HACE FALTA ESTO!!!!!!!!
                #filename = SextanteUtils.tempfolder + os.sep + os.path.dirname(filename) + ".sgrd"
                command+=(" -" + out.name + " " + filename);
            if isinstance(out, OutputVector):
                filename = out.value
                if not filename.endswith(".shp"):
                    filename += ".shp"
                    out.value = filename
                command+=(" -" + out.name + " " + filename);
            if isinstance(out, OutputTable):
                filename = out.value
                if not filename.endswith(".dbf"):
                    filename += ".dbf"
                    out.value = filename
                command+=(" -" + out.name + " " + filename);

        commands.append(command)

      #-------------------------------------- //3:Export resulting raster layers
      #----- for (int i = 0; i < m_OutputObjects.getOutputObjectsCount(); i++) {
         #--------------------- final Output out = m_OutputObjects.getOutput(i);
         #------------------------------ if (out instanceof OutputRasterLayer) {
            #--- final IOutputChannel channel = getOutputChannel(out.getName());
            #-------------------- if (!(channel instanceof FileOutputChannel)) {
               #-------------------- if (channel instanceof NullOutputChannel) {
                  #--------------------------------------------------- continue;
               #-------------------------------------------------------------- }
               #--------------------------------------------------------- else {
                  #-------------- throw new UnsupportedOutputChannelException();
               #-------------------------------------------------------------- }
            #----------------------------------------------------------------- }
            #-------- final FileOutputChannel foc = (FileOutputChannel) channel;
            #----------------------------- String sFilename = foc.getFilename();
            #--- if (!sFilename.endsWith("asc") && !sFilename.endsWith("tif")) {
               #-------------------------------- sFilename = sFilename + ".tif";
            #----------------------------------------------------------------- }
            # final String sFilename2 = SextanteGUI.getOutputFactory().getTempFolder() + File.separator
                                      # + new File(sFilename).getName() + ".sgrd";
            #---------------------------------- if (sFilename.endsWith("asc")) {
               # commands.add("io_grid 0 -GRID " + sFilename2 + " -FORMAT 1 -FILE " + sFilename);
            #----------------------------------------------------------------- }
            #------------------------------------------------------------ else {
               # commands.add("io_gdal 1 -GRIDS " + sFilename2 + " -FORMAT 1 -FILE " + sFilename);
            #----------------------------------------------------------------- }
         #-------------------------------------------------------------------- }
      #----------------------------------------------------------------------- }


      #4 Run SAGA

        SagaUtils.createSagaBatchJobFileFromSagaCommands(commands)
      #SagaUtils.executeSaga(this);

        return True


    def exportRasterLayer(self,layer):

        if not layer.lower().endswith("tif") and not layer.lower().endswith("asc"):
            raise SagaExecutionException("unsupported_file_format")
        ext = os.path.splitext(layer)[1][1:].split()
        destFilename = self.getTempFilename()
        self.exportedLayers.put(layer, destFilename)
        if ext.lower() == "tif":
            return "io_grid_image 1 -OUT_GRID " + destFilename + " -FILE " + layer + " -METHOD 0"
        else:
            return "io_grid 1 -GRID " + destFilename + " -FILE " + layer


    def getTempFilename(self):
        self.numExportedLayers+=1
        path = SextanteUtils.userFolder()
        filename = path + os.sep + str(time.time()) + str(self.numExportedLayers) + ".sgrd"

        return filename

