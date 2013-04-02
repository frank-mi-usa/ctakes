/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.temporal.eval;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ctakes.relationextractor.eval.RelationExtractorEvaluation.HashableArguments;
import org.apache.ctakes.temporal.ae.EventTimeRelationAnnotator;
import org.apache.ctakes.typesystem.type.relation.BinaryTextRelation;
import org.apache.ctakes.typesystem.type.relation.TemporalTextRelation;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.JCas;
import org.cleartk.classifier.jar.JarClassifierBuilder;
import org.cleartk.classifier.libsvm.LIBSVMStringOutcomeDataWriter;
import org.cleartk.eval.AnnotationStatistics;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.AggregateBuilder;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.pipeline.JCasIterable;
import org.uimafit.pipeline.SimplePipeline;
import org.uimafit.util.JCasUtil;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lexicalscope.jewel.cli.CliFactory;

public class EvaluationOfTemporalRelations extends
    Evaluation_ImplBase<AnnotationStatistics<String>> {

  public static void main(String[] args) throws Exception {
    Options options = CliFactory.parseArguments(Options.class, args);
    List<Integer> patientSets = options.getPatients().getList();
    List<Integer> trainItems = THYMEData.getTrainPatientSets(patientSets);
    List<Integer> devItems = THYMEData.getDevPatientSets(patientSets);
    EvaluationOfTemporalRelations evaluation = new EvaluationOfTemporalRelations(
        new File("target/eval/temporal-relations"),
        options.getRawTextDirectory(),
        options.getKnowtatorXMLDirectory(),
        options.getXMIDirectory(),
        options.getPrintErrors());
    evaluation.prepareXMIsFor(patientSets);
    AnnotationStatistics<String> stats = evaluation.trainAndTest(trainItems, devItems);
    System.err.println(stats);
  }

  public EvaluationOfTemporalRelations(
      File baseDirectory,
      File rawTextDirectory,
      File knowtatorXMLDirectory,
      File xmiDirectory,
      boolean printErrors) {
    super(baseDirectory, rawTextDirectory, knowtatorXMLDirectory, xmiDirectory);
    this.printErrors = printErrors;
  }

  @Override
  protected void train(CollectionReader collectionReader, File directory) throws Exception {
    AggregateBuilder aggregateBuilder = this.getPreprocessorAggregateBuilder();
    aggregateBuilder.add(CopyFromGold.getDescription(EventMention.class, TimeMention.class, BinaryTextRelation.class));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveNonTLINKRelations.class));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveCrossSentenceRelations.class));
    aggregateBuilder.add(EventTimeRelationAnnotator.createDataWriterDescription(
        LIBSVMStringOutcomeDataWriter.class,
        directory,
        1.0));
    SimplePipeline.runPipeline(collectionReader, aggregateBuilder.createAggregate());
    JarClassifierBuilder.trainAndPackage(directory, "-t", "2", "-d", "2", "-c", "10");
  }

  @Override
  protected AnnotationStatistics<String> test(CollectionReader collectionReader, File directory)
      throws Exception {
    AggregateBuilder aggregateBuilder = this.getPreprocessorAggregateBuilder();
    aggregateBuilder.add(CopyFromGold.getDescription(EventMention.class, TimeMention.class));
    aggregateBuilder.add(
        AnalysisEngineFactory.createPrimitiveDescription(RemoveNonTLINKRelations.class),
        CAS.NAME_DEFAULT_SOFA,
        GOLD_VIEW_NAME);
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(
        RemoveCrossSentenceRelations.class,
        RemoveCrossSentenceRelations.PARAM_RELATION_VIEW,
        GOLD_VIEW_NAME));
    aggregateBuilder.add(AnalysisEngineFactory.createPrimitiveDescription(RemoveRelations.class));
    aggregateBuilder.add(EventTimeRelationAnnotator.createAnnotatorDescription(directory));

    Function<BinaryTextRelation, ?> getSpan = new Function<BinaryTextRelation, HashableArguments>() {
      public HashableArguments apply(BinaryTextRelation relation) {
        return new HashableArguments(relation);
      }
    };
    Function<BinaryTextRelation, String> getOutcome = AnnotationStatistics.annotationToFeatureValue("category");

    AnnotationStatistics<String> stats = new AnnotationStatistics<String>();
    for (JCas jCas : new JCasIterable(collectionReader, aggregateBuilder.createAggregate())) {
      JCas goldView = jCas.getView(GOLD_VIEW_NAME);
      JCas systemView = jCas.getView(CAS.NAME_DEFAULT_SOFA);
      Collection<BinaryTextRelation> goldRelations = JCasUtil.select(
          goldView,
          BinaryTextRelation.class);
      Collection<BinaryTextRelation> systemRelations = JCasUtil.select(
          systemView,
          BinaryTextRelation.class);
      stats.add(goldRelations, systemRelations, getSpan, getOutcome);
      
      if(this.printErrors){
    	  Map<HashableArguments, BinaryTextRelation> goldMap = Maps.newHashMap();
    	  for (BinaryTextRelation relation : goldRelations) {
    		  goldMap.put(new HashableArguments(relation), relation);
    	  }
    	  Map<HashableArguments, BinaryTextRelation> systemMap = Maps.newHashMap();
    	  for (BinaryTextRelation relation : systemRelations) {
    		  systemMap.put(new HashableArguments(relation), relation);
    	  }
    	  Set<HashableArguments> all = Sets.union(goldMap.keySet(), systemMap.keySet());
    	  List<HashableArguments> sorted = Lists.newArrayList(all);
    	  Collections.sort(sorted);
    	  for (HashableArguments key : sorted) {
    		  BinaryTextRelation goldRelation = goldMap.get(key);
    		  BinaryTextRelation systemRelation = systemMap.get(key);
    		  if (goldRelation == null) {
    			  System.out.println("System added: " + formatRelation(systemRelation));
    		  } else if (systemRelation == null) {
    			  System.out.println("System dropped: " + formatRelation(goldRelation));
    		  } else if (!systemRelation.getCategory().equals(goldRelation.getCategory())) {
    			  String label = systemRelation.getCategory();
    			  System.out.printf("System labeled %s for %s\n", label, formatRelation(systemRelation));
    		  }
    	  }
      }
    }
    return stats;
  }

  private static String formatRelation(BinaryTextRelation relation) {
	  IdentifiedAnnotation arg1 = (IdentifiedAnnotation)relation.getArg1().getArgument();
	  IdentifiedAnnotation arg2 = (IdentifiedAnnotation)relation.getArg2().getArgument();
	  String text = arg1.getCAS().getDocumentText();
	  int begin = Math.min(arg1.getBegin(), arg2.getBegin());
	  int end = Math.max(arg1.getBegin(), arg2.getBegin());
	  begin = Math.max(0, begin - 50);
	  end = Math.min(text.length(), end + 50);
	  return String.format(
			  "%s(%s(type=%d), %s(type=%d)) in ...%s...",
			  relation.getCategory(),
			  arg1.getCoveredText(),
			  arg1.getTypeID(),
			  arg2.getCoveredText(),
			  arg2.getTypeID(),
			  text.substring(begin, end).replaceAll("[\r\n]", " "));
  }

  public static class RemoveNonTLINKRelations extends JCasAnnotator_ImplBase {
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      for (BinaryTextRelation relation : Lists.newArrayList(JCasUtil.select(
          jCas,
          BinaryTextRelation.class))) {
        if (!(relation instanceof TemporalTextRelation)) {
          relation.getArg1().removeFromIndexes();
          relation.getArg2().removeFromIndexes();
          relation.removeFromIndexes();
        }
      }
    }
  }

  public static class RemoveCrossSentenceRelations extends JCasAnnotator_ImplBase {

    public static final String PARAM_RELATION_VIEW = "RelationView";

    @ConfigurationParameter(name = PARAM_RELATION_VIEW)
    private String relationViewName = CAS.NAME_DEFAULT_SOFA;

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      JCas relationView;
      try {
        relationView = jCas.getView(this.relationViewName);
      } catch (CASException e) {
        throw new AnalysisEngineProcessException(e);
      }

      // map events and times to the sentences that contain them
      Map<IdentifiedAnnotation, Integer> sentenceIndex = Maps.newHashMap();
      int index = -1;
      for (Sentence sentence : JCasUtil.select(jCas, Sentence.class)) {
        ++index;
        for (EventMention event : JCasUtil.selectCovered(relationView, EventMention.class, sentence)) {
          sentenceIndex.put(event, index);
        }
        for (TimeMention time : JCasUtil.selectCovered(relationView, TimeMention.class, sentence)) {
          sentenceIndex.put(time, index);
        }
      }

      // remove any relations that are in different sentences.
      for (BinaryTextRelation relation : Lists.newArrayList(JCasUtil.select(
          relationView,
          BinaryTextRelation.class))) {
        Integer sent1 = sentenceIndex.get(relation.getArg1().getArgument());
        Integer sent2 = sentenceIndex.get(relation.getArg2().getArgument());
        if (sent1 == null || sent2 == null || !sent1.equals(sent2)) {
          relation.getArg1().removeFromIndexes();
          relation.getArg2().removeFromIndexes();
          relation.removeFromIndexes();
        }
      }
    }
  }

  public static class RemoveRelations extends JCasAnnotator_ImplBase {
    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
      for (BinaryTextRelation relation : Lists.newArrayList(JCasUtil.select(
          jCas,
          BinaryTextRelation.class))) {
        relation.getArg1().removeFromIndexes();
        relation.getArg2().removeFromIndexes();
        relation.removeFromIndexes();
      }
    }
  }
}