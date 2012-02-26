from sextante.core.GeoAlgorithm import GeoAlgorithm
import os.path
from PyQt4 import QtGui
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from qgis.core import *
from sextante.parameters.ParameterVector import ParameterVector
from sextante.core.QGisLayers import QGisLayers
from sextante.core.GeoAlgorithmExecutionException import GeoAlgorithmExecutionException
from sextante.outputs.OutputVector import OutputVector
from sextante.ftools import ftools_utils

class ExtractNodesAlgorithm(GeoAlgorithm):

    INPUT = "INPUT"
    OUTPUT = "OUTPUT"

    def getIcon(self):
        return QtGui.QIcon(os.path.dirname(__file__) + "/icons/extract_nodes.png")

    def processAlgorithm(self, progress):
        settings = QSettings()
        systemEncoding = settings.value( "/UI/encoding", "System" ).toString()
        output = self.getOutputValue(ExtractNodesAlgorithm.OUTPUT)
        vlayer = QGisLayers.getObjectFromUri(self.getParameterValue(ExtractNodesAlgorithm.INPUT))
        vprovider = vlayer.dataProvider()
        allAttrs = vprovider.attributeIndexes()
        vprovider.select( allAttrs )
        fields = vprovider.fields()
        writer = QgsVectorFileWriter( output, systemEncoding, fields, QGis.WKBPoint, vprovider.crs() )
        inFeat = QgsFeature()
        outFeat = QgsFeature()
        inGeom = QgsGeometry()
        outGeom = QgsGeometry()
        nFeat = vprovider.featureCount()
        nElement = 0
        while vprovider.nextFeature( inFeat ):
          nElement += 1
          progress.setPercentage(int(nElement/nFeat * 100))
          inGeom = inFeat.geometry()
          atMap = inFeat.attributeMap()
          pointList = ftools_utils.extractPoints( inGeom )
          outFeat.setAttributeMap( atMap )
          for i in pointList:
            outFeat.setGeometry( outGeom.fromPoint( i ) )
            writer.addFeature( outFeat )
        del writer

    def defineCharacteristics(self):
        self.name = "Extract nodes"
        self.group = "Geometry tools"
        self.addParameter(ParameterVector(ExtractNodesAlgorithm.INPUT, "Input layer", ParameterVector.VECTOR_TYPE_ANY))
        self.addOutput(OutputVector(ExtractNodesAlgorithm.OUTPUT, "Output layer"))
    #=========================================================