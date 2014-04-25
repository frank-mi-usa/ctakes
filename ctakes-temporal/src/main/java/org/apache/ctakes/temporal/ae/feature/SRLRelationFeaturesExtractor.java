package org.apache.ctakes.temporal.ae.feature;

import java.util.List;

import org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.Predicate;
import org.apache.ctakes.typesystem.type.textsem.SemanticArgument;
import org.apache.ctakes.typesystem.type.textsem.SemanticRoleRelation;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.cleartk.classifier.Feature;
import org.uimafit.util.JCasUtil;

import com.google.common.collect.Lists;

public class SRLRelationFeaturesExtractor implements RelationFeaturesExtractor {

  /*
   * (non-Javadoc)
   * @see org.apache.ctakes.relationextractor.ae.features.RelationFeaturesExtractor#extract(org.apache.uima.jcas.JCas, org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation, org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation)
   * This feature extractor uses semantic role labeling features -- is either
   * argument a predicate, if so what frame, and if so is the other argument a semantic arg to that predicate.
   * Also are features for whether individual args are just any semantic arg to any semantic relation and what arg type.
   */
  public List<Feature> extract(JCas jCas, IdentifiedAnnotation arg1,
      IdentifiedAnnotation arg2) throws AnalysisEngineProcessException {
    List<Feature> feats = Lists.newArrayList();
    
    if(arg1 instanceof EventMention){
      List<Predicate> preds = JCasUtil.selectCovered(Predicate.class, arg1);
      if(preds.size() > 0){
        feats.add(new Feature("Arg1_Pred", true));
        feats.add(new Feature("Arg1_Frame", preds.get(0).getFrameSet()));
        // check if arg2 is one of its SRL args
        FSList relList = preds.get(0).getRelations();
        while(relList instanceof NonEmptyFSList){
          SemanticRoleRelation rel = (SemanticRoleRelation) ((NonEmptyFSList)relList).getHead();
          SemanticArgument arg = rel.getArgument();
          if(arg.getBegin() == arg2.getBegin() && arg.getEnd() == arg2.getEnd()){
            feats.add(new Feature("Arg1_Pred_Arg2_Role", true));
            break;
          }
          relList = ((NonEmptyFSList)relList).getTail();
        }
      }
      List<SemanticArgument> args = JCasUtil.selectCovered(SemanticArgument.class, arg1);
      if(args.size() > 0){
        feats.add(new Feature("Arg1_SemArg", true));
        feats.add(new Feature("Arg1_SemArgType", args.get(0).getLabel()));
      }
    }
    
    if(arg2 instanceof EventMention){
      List<Predicate> preds = JCasUtil.selectCovered(Predicate.class, arg2);
      if(preds.size() > 0){
        feats.add(new Feature("Arg2_Pred", true));
        feats.add(new Feature("Arg2_Frame", preds.get(0).getFrameSet()));
        // check if arg2 is one of its SRL args
        FSList relList = preds.get(0).getRelations();
        while(relList instanceof NonEmptyFSList){
          SemanticRoleRelation rel = (SemanticRoleRelation) ((NonEmptyFSList)relList).getHead();
          SemanticArgument arg = rel.getArgument();
          if(arg.getBegin() == arg1.getBegin() && arg.getEnd() == arg1.getEnd()){
            feats.add(new Feature("Arg2_Pred_Arg1_Role", true));
            break;
          }
          relList = ((NonEmptyFSList)relList).getTail();
        }
      }
      List<SemanticArgument> args = JCasUtil.selectCovered(SemanticArgument.class, arg2);
      if(args.size() > 0){
        feats.add(new Feature("Arg2_SemArg", true));
        feats.add(new Feature("Arg2_SemArgType", args.get(0).getLabel()));
      }
    }
    
    return feats;
  }

}