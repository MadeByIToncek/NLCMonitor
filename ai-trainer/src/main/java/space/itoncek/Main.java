package space.itoncek;

/*import org.datavec.api.io.filters.PathFilter;
import org.datavec.api.io.filters.RandomPathFilter;
import org.datavec.api.io.labels.ParentPathLabelGenerator;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.image.recordreader.ImageRecordReader;
import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.iterator.CombinedPreProcessor;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.storage.InMemoryStatsStorage;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImageFlatteningDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;*/

import java.io.IOException;

public class Main {
	public static void main(String[] args) throws IOException {
//		trainCSV();
//		trainImage();
	}

	public static void trainImage() throws IOException {
		/*UIServer uiServer = UIServer.getInstance();
		//number of rows and columns in the input pictures
		final int numRows = 46;
		final int numColumns = 46;
		int numEpochs = 16; // number of epochs to perform

		FileSplit fs = new FileSplit(new File("./data/"));
		PathFilter pf = new RandomPathFilter(new Random(), "png");
		//Get the DataSetIterators:
		double ratio = .9;
		InputSplit[] is = fs.sample(pf, ratio,1-ratio);
		ImageRecordReader irr = new ImageRecordReader(numRows,numColumns,new ParentPathLabelGenerator());
		ImageRecordReader irr2 = new ImageRecordReader(numRows,numColumns,new ParentPathLabelGenerator());

		irr.initialize(is[0]);
		irr2.initialize(is[1]);

		ImageFlatteningDataSetPreProcessor ifdspp = new ImageFlatteningDataSetPreProcessor();

		DataNormalization imageScaler = new ImagePreProcessingScaler();
		DataNormalization imageScaler2 = new ImagePreProcessingScaler();

		CombinedPreProcessor cpp = new CombinedPreProcessor.Builder().addPreProcessor(imageScaler).addPreProcessor(ifdspp).build();
		CombinedPreProcessor cpp2 = new CombinedPreProcessor.Builder().addPreProcessor(imageScaler2).addPreProcessor(ifdspp).build();

		DataSetIterator train = new RecordReaderDataSetIterator(irr,64);
		DataSetIterator test = new RecordReaderDataSetIterator(irr2,16);

		imageScaler.fit(train);
		imageScaler2.fit(test);

		train.setPreProcessor(cpp);
		test.setPreProcessor(cpp2);

		MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
				.optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
				.updater(new Sgd(0.05))
				.list()
				.layer(new DenseLayer.Builder()
						.nIn(numRows * numColumns) // Number of input datapoints.
						.nOut(1000) // Number of output datapoints.
						.activation(Activation.RELU) // Activation function.
						.weightInit(WeightInit.XAVIER) // Weight initialization.
						.build())
				.layer(new DenseLayer.Builder()
						.nIn(1000) // Number of input datapoints.
						.nOut(200) // Number of output datapoints.
						.activation(Activation.RELU) // Activation function.
						.weightInit(WeightInit.XAVIER) // Weight initialization.
						.build())
				.layer(new DenseLayer.Builder()
						.nIn(200) // Number of input datapoints.
						.nOut(100) // Number of output datapoints.
						.activation(Activation.RELU) // Activation function.
						.weightInit(WeightInit.XAVIER) // Weight initialization.
						.build())
				.layer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
						.nIn(100)
						.nOut(irr.numLabels())
						.activation(Activation.SOFTMAX)
						.weightInit(WeightInit.XAVIER)
						.build())
				.build();

		MultiLayerNetwork model = new MultiLayerNetwork(conf);

		StatsStorage statsStorage = new InMemoryStatsStorage();
		uiServer.attach(statsStorage);
		model.init();
//		Nd4j.getMemoryManager().togglePeriodicGc(false);
		model.setListeners(List.of(new StatsListener(statsStorage)));

		train.reset();
		test.reset();

		model.fit(train, numEpochs);

		Evaluation eval = model.evaluate(test);
		model.save(new File("./data/model.ai"),false);
		Logger.getLogger("Output").info(eval.stats());*/
	}
}