package org.protege.owl.diff;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.junit.*;
import org.protege.owl.diff.align.AlignmentAlgorithm;
import org.protege.owl.diff.align.OwlDiffMap;
import org.protege.owl.diff.align.algorithms.*;
import org.protege.owl.diff.present.*;
import org.protege.owl.diff.present.EntityBasedDiff.DiffType;
import org.protege.owl.diff.present.algorithms.*;
import org.protege.owl.diff.service.CodeToEntityMapper;
import org.protege.owl.diff.service.RetirementClassService;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

public class PresentationAlgorithmTest extends TestCase {
    private OWLDataFactory factory;
    private OWLOntology ontology1;
    private OWLOntology ontology2;

    private void loadOntologies(String prefix) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        ontology1 = manager.loadOntologyFromOntologyDocument(new File(JunitUtilities.PROJECTS_DIRECTORY + prefix + "-Left.owl"));
        OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
        ontology2 = manager2.loadOntologyFromOntologyDocument(new File(JunitUtilities.PROJECTS_DIRECTORY + prefix + "-Right.owl"));
        factory = manager.getOWLDataFactory();
    }

    public void testMerge() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/Merge.owl";
        loadOntologies("Merge");
        Map<String, String> p = new HashMap<String, String>();
        p.put(CodeToEntityMapper.CODE_ANNOTATION_PROPERTY, ns + "#code");
        p.put(RetirementClassService.RETIREMENT_CLASS_PROPERTY, ns + "#Retired");
        p.put(RetirementClassService.RETIREMENT_STATUS_PROPERTY, ns + "#Status");
        p.put(RetirementClassService.RETIREMENT_STATUS_STRING, "Retired_Concept");
        p.put(IdentifyMergedConcepts.MERGED_INTO_ANNOTATION_PROPERTY, ns + "#Merge_Into");
        Engine e = new Engine(ontology1, ontology2);
        e.setParameters(p);
        e.setAlignmentAlgorithms(new MatchByCode(), new MatchById());
        e.setPresentationAlgorithms(new IdentifyMergedConcepts());
        e.phase1();
        e.phase2();
        Changes changes = e.getChanges();
        EntityBasedDiff keptEntityDiffs = changes.getSourceDiffMap().get(factory.getOWLClass(IRI.create(ns + "#A")));
        EntityBasedDiff retiredEntityDiffs = changes.getSourceDiffMap().get(factory.getOWLClass(IRI.create(ns + "#B")));

        int mergeCount = 0;
        int retiredCount = 0;
        for (MatchedAxiom match : retiredEntityDiffs.getAxiomMatches()) {
            MatchDescription description = match.getDescription();
            if (description.equals(IdentifyMergedConcepts.MERGE)) {
                mergeCount++;
            } else if (description.equals(IdentifyMergedConcepts.RETIRED_DUE_TO_MERGE)) {
                retiredCount++;
            }
        }
        assertTrue(mergeCount == 1);
        assertTrue(retiredCount == 2);

        int modifiedCount = 0;
        for (MatchedAxiom match : keptEntityDiffs.getAxiomMatches()) {
            if (match.getDescription().equals(IdentifyMergedConcepts.MERGE_AXIOM)) {
                modifiedCount++;
            }
        }
        assertTrue(modifiedCount == 1);
        e.display();
    }

    /**
     * This is the same as the previous test except the retirement step is at a lower priority and
     * does not kick in.
     */
    public void testMergeWithVacuousRetire() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/Merge.owl";
        loadOntologies("Merge");

        Map<String, String> p = new HashMap<String, String>();
        p.put(CodeToEntityMapper.CODE_ANNOTATION_PROPERTY, ns + "#code");
        p.put(RetirementClassService.RETIREMENT_CLASS_PROPERTY, ns + "#Retired");
        p.put(RetirementClassService.RETIREMENT_STATUS_PROPERTY, ns + "#Status");
        p.put(RetirementClassService.RETIREMENT_STATUS_STRING, "Retired_Concept");
        p.put(IdentifyMergedConcepts.MERGED_INTO_ANNOTATION_PROPERTY, ns + "#Merge_Into");

        Engine e = new Engine(ontology1, ontology2);
        e.setParameters(p);
        e.setAlignmentAlgorithms(new MatchByCode(), new MatchById());
        e.setPresentationAlgorithms(new IdentifyMergedConcepts(), new IdentifyRetiredConcepts());
        e.phase1();
        e.phase2();
        Changes changes = e.getChanges();
        EntityBasedDiff keptEntityDiffs = changes.getSourceDiffMap().get(factory.getOWLClass(IRI.create(ns + "#A")));
        EntityBasedDiff retiredEntityDiffs = changes.getSourceDiffMap().get(factory.getOWLClass(IRI.create(ns + "#B")));

        int mergeCount = 0;
        int retiredCount = 0;
        for (MatchedAxiom match : retiredEntityDiffs.getAxiomMatches()) {
            MatchDescription description = match.getDescription();
            if (description.equals(IdentifyMergedConcepts.MERGE)) {
                mergeCount++;
            } else if (description.equals(IdentifyMergedConcepts.RETIRED_DUE_TO_MERGE)) {
                retiredCount++;
            }
        }
        assertTrue(mergeCount == 1);
        assertTrue(retiredCount == 2);

        int modifiedCount = 0;
        for (MatchedAxiom match : keptEntityDiffs.getAxiomMatches()) {
            if (match.getDescription().equals(IdentifyMergedConcepts.MERGE_AXIOM)) {
                modifiedCount++;
            }
        }
        assertTrue(modifiedCount == 1);
        e.display();
    }

    public void testRetire() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/SimpleRetire.owl";
        loadOntologies("SimpleRetire");

        Map<String, String> p = new HashMap<String, String>();
        p.put(RetirementClassService.RETIREMENT_CLASS_PROPERTY, ns + "#Retire");
        p.put(RetirementClassService.RETIREMENT_STATUS_STRING, "Retired_Concept");
        p.put(RetirementClassService.RETIREMENT_STATUS_PROPERTY, ns + "#Concept_Status");
        p.put(RetirementClassService.RETIREMENT_META_PROPERTIES + 0, ns + "#OLD_PARENT");
        p.put(RetirementClassService.RETIREMENT_META_PROPERTIES + 1, ns + "#OLD_CONTEXT");

        Engine e = new Engine(ontology1, ontology2);
        e.setParameters(p);
        e.setAlignmentAlgorithms(new MatchById());
        e.setPresentationAlgorithms(new IdentifyRetiredConcepts());
        e.phase1();
        e.phase2();
        int retiredSubClassCount = 0;
        int retiredAnnotationCount = 0;
        int deletedDueToRetirementCount = 0;
        for (EntityBasedDiff diff : e.getChanges().getEntityBasedDiffs()) {
            if (diff.getDiffType().equals(DiffType.EQUIVALENT)) {
                continue;
            }
            for (MatchedAxiom match : diff.getAxiomMatches()) {
                if (match.getDescription().equals(IdentifyRetiredConcepts.RETIRED)) {
                    assertTrue(match.getSourceAxiom() == null);
                    if (match.getTargetAxiom() instanceof OWLSubClassOfAxiom) {
                        retiredSubClassCount++;
                    } else if (match.getTargetAxiom() instanceof OWLAnnotationAssertionAxiom) {
                        retiredAnnotationCount++;
                    }
                } else if (match.getDescription().equals(IdentifyRetiredConcepts.DELETED_DUE_TO_RETIRE)) {
                    deletedDueToRetirementCount++;
                }
            }
        }
        assertTrue(retiredAnnotationCount == 4);
        assertTrue(retiredSubClassCount == 1);
        assertTrue(deletedDueToRetirementCount == 1);
        e.display();
    }

    /**
     * This test is one of three tests to ensures that the mechanism that gets the entities
     * an axiom is "about" properly depends on the source/target ontology.
     *
     * @throws OWLOntologyCreationException
     */
    public void testAddAnnotation() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/AddAnnotation.owl";
        loadOntologies("AddAnnotation");
        Map<String, String> p = new HashMap<String, String>();
        Engine e = new Engine(ontology1, ontology2);
        e.setParameters(p);
        e.setAlignmentAlgorithms(new MatchById());
        e.phase1();
        e.phase2();
        Changes changes = e.getChanges();
        OWLClass newEntity = e.getOWLDataFactory().getOWLClass(IRI.create(ns + "#B"));
        EntityBasedDiff diff = changes.getTargetDiffMap().get(newEntity);
        int addedAnnotationCount = 0;
        for (MatchedAxiom match : diff.getAxiomMatches()) {
            if (match.getDescription().equals(MatchedAxiom.AXIOM_ADDED) && match.getTargetAxiom() instanceof OWLAnnotationAssertionAxiom) {
                addedAnnotationCount++;
            }
        }
        assertTrue(addedAnnotationCount == 1);
    }

    /**
     * This test is one of three tests to ensures that the mechanism that gets the entities
     * an axiom is "about" properly depends on the source/target ontology.
     *
     * @throws OWLOntologyCreationException
     */
    public void testRemoveAnnotation() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/RemoveAnnotation.owl";
        loadOntologies("RemoveAnnotation");
        Map<String, String> p = new HashMap<String, String>();
        Engine e = new Engine(ontology1, ontology2);
        e.setParameters(p);
        e.setAlignmentAlgorithms(new MatchById());
        e.phase1();
        e.phase2();
        Changes changes = e.getChanges();
        OWLClass newEntity = e.getOWLDataFactory().getOWLClass(IRI.create(ns + "#A"));
        EntityBasedDiff diff = changes.getSourceDiffMap().get(newEntity);
        int deletedAnnotationCount = 0;
        for (MatchedAxiom match : diff.getAxiomMatches()) {
            if (match.getDescription().equals(MatchedAxiom.AXIOM_DELETED) && match.getSourceAxiom() instanceof OWLAnnotationAssertionAxiom) {
                deletedAnnotationCount++;
            }
        }
        assertTrue(deletedAnnotationCount == 1);
    }

    /**
     * This test is one of three tests to ensures that the mechanism that gets the entities
     * an axiom is "about" properly depends on the source/target ontology.
     *
     * @throws OWLOntologyCreationException
     */
    public void testSourceCalculationInAddRemove() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/SourceCalculation.owl";
        OWLDataFactory factory = OWLManager.getOWLDataFactory();
        OWLAnnotationProperty label = factory.getRDFSLabel();
        OWLLiteral literal = factory.getOWLLiteral("hello world", "en");

        OWLOntologyManager manager1 = OWLManager.createOWLOntologyManager();
        OWLOntology ontology1 = manager1.createOntology(IRI.create(ns));
        OWLClass a = factory.getOWLClass(IRI.create(ns + "#A"));
        manager1.addAxiom(ontology1, factory.getOWLDeclarationAxiom(a));

        OWLOntologyManager manager2 = OWLManager.createOWLOntologyManager();
        OWLOntology ontology2 = manager2.createOntology(IRI.create(ns));
        OWLClass b = factory.getOWLClass(IRI.create(ns + "#B"));
        manager2.addAxiom(ontology2, factory.getOWLDeclarationAxiom(b));

        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById());
        e.phase1();
        e.phase2();

        Changes changes = e.getChanges();

        EntityBasedDiff aDiff = changes.getSourceDiffMap().get(a);
        OWLAnnotationAssertionAxiom aAnnot = factory.getOWLAnnotationAssertionAxiom(a.getIRI(), factory.getOWLAnnotation(label, literal));
        MatchedAxiom aMatch = new MatchedAxiom(aAnnot, null, MatchedAxiom.AXIOM_DELETED);
        changes.addMatch(aMatch);
        assertTrue(aDiff.getAxiomMatches().contains(aMatch));
        changes.removeMatch(aMatch);
        assertTrue(!aDiff.getAxiomMatches().contains(aMatch));

        EntityBasedDiff bDiff = changes.getTargetDiffMap().get(b);
        OWLAnnotationAssertionAxiom bAnnot = factory.getOWLAnnotationAssertionAxiom(b.getIRI(), factory.getOWLAnnotation(label, literal));
        MatchedAxiom bMatch = new MatchedAxiom(null, bAnnot, MatchedAxiom.AXIOM_ADDED);
        changes.addMatch(bMatch);
        assertTrue(bDiff.getAxiomMatches().contains(bMatch));
        changes.removeMatch(bMatch);
        assertTrue(!bDiff.getAxiomMatches().contains(bMatch));
    }

    public void testMatchLoneSuperClasses() throws OWLOntologyCreationException {
        String ns = "http://protege.org/ontologies/MatchClasses.owl";
        loadOntologies("MatchSuperClasses");
        Map<String, String> p = new HashMap<String, String>();
        Engine e = new Engine(ontology1, ontology2);
        e.setParameters(p);
        e.setAlignmentAlgorithms(new MatchById());
        e.setPresentationAlgorithms(new IdentifyChangedSuperclass());
        e.phase1();
        e.phase2();
        Changes changes = e.getChanges();
        assertEquals(3, changes.getEntityBasedDiffs().size());
        for (EntityBasedDiff diff : changes.getEntityBasedDiffs()) {
            for (MatchedAxiom match : diff.getAxiomMatches()) {
                assertEquals(IdentifyChangedSuperclass.CHANGED_SUPERCLASS, match.getDescription());
            }
        }
        Map<OWLEntity, EntityBasedDiff> sourceDiffMap = changes.getSourceDiffMap();
        EntityBasedDiff c00Diff = sourceDiffMap.get(factory.getOWLClass(IRI.create(ns + "#C00")));
        assertNull(c00Diff);
        EntityBasedDiff c10Diff = sourceDiffMap.get(factory.getOWLClass(IRI.create(ns + "#C10")));
        assertEquals(1, c10Diff.getAxiomMatches().size());
        EntityBasedDiff c01Diff = sourceDiffMap.get(factory.getOWLClass(IRI.create(ns + "#C01")));
        assertEquals(1, c01Diff.getAxiomMatches().size());
        EntityBasedDiff c11Diff = sourceDiffMap.get(factory.getOWLClass(IRI.create(ns + "#C11")));
        assertEquals(2, c11Diff.getAxiomMatches().size());
    }

    public void testChangedDefinition() throws OWLOntologyCreationException {
        loadOntologies("ChangedDefinition");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById());
        e.setPresentationAlgorithms(new IdentifyChangedDefinition());
        e.phase1();
        e.phase2();
        Changes changes = e.getChanges();
        assertEquals(1, changes.getEntityBasedDiffs().size());
        EntityBasedDiff ediff = changes.getEntityBasedDiffs().iterator().next();
        assertEquals(1, ediff.getAxiomMatches().size());
        assertEquals(IdentifyChangedDefinition.CHANGED_DEFINITION, ediff.getAxiomMatches().iterator().next().getDescription());
    }

    public void testOrphanedAnnotationsBaseline() throws OWLOntologyCreationException {
        loadOntologies("OrphanedAnnotation");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById(), new SuperSubClassPinch());
        e.setPresentationAlgorithms(new IdentifyRenameOperation());
        e.phase1();
        assertTrue(e.getOwlDiffMap().getUnmatchedSourceAxioms().isEmpty());
        assertTrue(e.getOwlDiffMap().getUnmatchedTargetAxioms().isEmpty());
        e.phase2();
        assertEquals(1, e.getChanges().getEntityBasedDiffs().size());
        EntityBasedDiff diff = e.getChanges().getEntityBasedDiffs().iterator().next();
        assertEquals(1, diff.getAxiomMatches().size());
        MatchedAxiom match = diff.getAxiomMatches().iterator().next();
        assertEquals(IdentifyRenameOperation.RENAMED_CHANGE_DESCRIPTION, match.getDescription());
    }

    public void testOrphanedAnnotations() throws OWLOntologyCreationException {
        loadOntologies("OrphanedAnnotation");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById(), new SuperSubClassPinch());
        e.setPresentationAlgorithms(new IdentifyRenameOperation(), new IdentifyOrphanedAnnotations());
        e.phase1();
        assertTrue(e.getOwlDiffMap().getUnmatchedSourceAxioms().isEmpty());
        assertTrue(e.getOwlDiffMap().getUnmatchedTargetAxioms().isEmpty());
        e.phase2();
        assertEquals(1, e.getChanges().getEntityBasedDiffs().size());
        EntityBasedDiff diff = e.getChanges().getEntityBasedDiffs().iterator().next();
        assertEquals(2, diff.getAxiomMatches().size());
        int counter = 0;
        for (MatchedAxiom match : diff.getAxiomMatches()) {
            counter++;
            if (counter == 1) {
                assertEquals(IdentifyRenameOperation.RENAMED_CHANGE_DESCRIPTION, match.getDescription());
            } else {
                assertEquals(IdentifyOrphanedAnnotations.ORPHANED_ANNOTATION, match.getDescription());
                assertTrue(match.getSourceAxiom().equals(match.getTargetAxiom()));
                assertTrue(match.getSourceAxiom() instanceof OWLAnnotationAssertionAxiom);
            }
        }
    }

    public void testOrphanedAnnotationsWithPun() throws OWLOntologyCreationException {
        loadOntologies("OrphanedAnnotationWithPun");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById(), new SuperSubClassPinch());
        e.setPresentationAlgorithms(new IdentifyRenameOperation(), new IdentifyOrphanedAnnotations());
        e.phase1();
        assertTrue(e.getOwlDiffMap().getUnmatchedSourceAxioms().isEmpty());
        assertTrue(e.getOwlDiffMap().getUnmatchedTargetAxioms().isEmpty());
        e.phase2();
        assertEquals(1, e.getChanges().getEntityBasedDiffs().size());
        EntityBasedDiff diff = e.getChanges().getEntityBasedDiffs().iterator().next();
        assertEquals(1, diff.getAxiomMatches().size());
        MatchedAxiom match = diff.getAxiomMatches().iterator().next();
        assertEquals(IdentifyRenameOperation.RENAMED_CHANGE_DESCRIPTION, match.getDescription());
    }

    public void testOrphanedAnnotationsNot() throws OWLOntologyCreationException {
        loadOntologies("OrphanedAnnotationNot");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById(), new SuperSubClassPinch());
        e.setPresentationAlgorithms(new IdentifyRenameOperation(), new IdentifyAnnotationRefactored(), new IdentifyChangedAnnotation(), new IdentifyOrphanedAnnotations());
        e.phase1();
        assertEquals(0, e.getOwlDiffMap().getUnmatchedSourceAxioms().size());
        assertEquals(0, e.getOwlDiffMap().getUnmatchedTargetAxioms().size());
        e.phase2();
        assertEquals(1, e.getChanges().getEntityBasedDiffs().size());
        EntityBasedDiff diff = e.getChanges().getEntityBasedDiffs().iterator().next();
        assertEquals(1, diff.getAxiomMatches().size());
    }

    public void testAnnotationChanged() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new MatchById(), new SuperSubClassPinch());
        e.setPresentationAlgorithms(new IdentifyRenameOperation(), new IdentifyAnnotationRefactored(), new IdentifyChangedAnnotation(), new IdentifyOrphanedAnnotations());
        e.phase1();
        assertEquals(1, e.getOwlDiffMap().getUnmatchedSourceAxioms().size());
        assertEquals(1, e.getOwlDiffMap().getUnmatchedTargetAxioms().size());
        for (OWLAxiom sourceAxiom : e.getOwlDiffMap().getUnmatchedSourceAxioms()) {
            assertTrue(sourceAxiom instanceof OWLAnnotationAssertionAxiom);
        }
        for (OWLAxiom targetAxiom : e.getOwlDiffMap().getUnmatchedTargetAxioms()) {
            assertTrue(targetAxiom instanceof OWLAnnotationAssertionAxiom);
        }
        e.phase2();
        assertEquals(1, e.getChanges().getEntityBasedDiffs().size());
        EntityBasedDiff diff = e.getChanges().getEntityBasedDiffs().iterator().next();
        assertEquals(2, diff.getAxiomMatches().size());
        int counter = 0;
        for (MatchedAxiom match : diff.getAxiomMatches()) {
            counter++;
            if (counter == 1) {
                assertEquals(IdentifyRenameOperation.RENAMED_CHANGE_DESCRIPTION, match.getDescription());
            } else {
                OWLDataFactory factory = e.getOWLDataFactory();
                assertEquals(IdentifyChangedAnnotation.CHANGED_ANNOTATION, match.getDescription());
                assertTrue(match.getSourceAxiom() instanceof OWLAnnotationAssertionAxiom);
                assertTrue(match.getTargetAxiom() instanceof OWLAnnotationAssertionAxiom);
                OWLAnnotationAssertionAxiom sourceAxiom = (OWLAnnotationAssertionAxiom) match.getSourceAxiom();
                assertTrue(sourceAxiom.getAnnotation().getProperty().equals(factory.getRDFSLabel()));
            }
        }
    }


    public void testInitialiseIdentifySplitConcepts_NotInitalisedEngine() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);
        try {
            IdentifySplitConcepts identifySplitConcepts = new IdentifySplitConcepts();
            identifySplitConcepts.initialise(e);
            fail("NullPointerException expected");
        } catch (NullPointerException ex) {
            //We get a NullPointerException, because the engine.phase1() is not called. Is it OK?
        } catch (Exception ex) {
            fail("NullPointerException expected");

        }

    }

    public void testEnginePresentationAlgorithmsSort() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);

        e.setPresentationAlgorithms(new IdentifyOrphanedAnnotations(), new IdentifyOrphanedAnnotations(), new IdentifyAxiomAnnotationChanged(), new IdentifySplitConcepts(), new IdentifyAxiomAnnotationChanged(), new IdentifyChangedSuperclass(), new IdentifyOrphanedAnnotations());
        int prior = 10;
        int nextPrior = 10;
        for (PresentationAlgorithm algorithm : e.getPresentationAlgorithms()) {
            nextPrior = algorithm.getPriority();
            if (nextPrior > prior) {
                fail("bad sort");
            }
            prior = nextPrior;
        }

        //The algorithm with the biggest priority value is the most important, and min is 1, max is 10, so it works perfectly.
    }

    public void testTryToAddOnePresentAlgorithmMuchTimeToAnEngine() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);
        e.setPresentationAlgorithms(new IdentifyAxiomAnnotationChanged(), new IdentifyAxiomAnnotationChanged(), new IdentifyAxiomAnnotationChanged());
        assertEquals(3, e.getPresentationAlgorithms().size());
        //You can add one algorithm more time
    }

    public void testTryToAddOneAlignAlgorithmMuchTimeToAnEngine() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);
        e.setAlignmentAlgorithms(new DeferDeprecationAlgorithm(), new DeferDeprecationAlgorithm(), new DeferDeprecationAlgorithm());
        assertEquals(3, e.getAlignmentAlgorithms().size());
        //You can add one algorithm more time
    }

    public void testEngineAlignmentAlgorithmsSort() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);

        e.setAlignmentAlgorithms(new DeferDeprecationAlgorithm(), new MatchById(), new MatchById(), new MatchByCode(), new SuperSubClassPinch(), new MatchStandardVocabulary());
        int prior = 10;
        int nextPrior = 10;
        for (AlignmentAlgorithm algorithm : e.getAlignmentAlgorithms()) {
            nextPrior = algorithm.getPriority();
            if (nextPrior > prior) {
                fail("bad sort");
            }
            prior = nextPrior;
        }
        //The algorithm with the biggest priority value is the most important, and min is 1, max is 10, so it works perfectly.
    }

    public void testEngineGetOwlDiffMap() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);

        OwlDiffMap diff = e.getOwlDiffMap();
        assertNull(diff);

        //you need to call magic method Engine.phase1() to get engine work. Why isn't it called in ctor?

    }

    public void testEnginePhaseMethods() throws OWLOntologyCreationException {
        loadOntologies("AnnotationChanged");
        Engine e = new Engine(ontology1, ontology2);
        // e.phase2();
        e.phase1();

        //if you call phase2() before phase1(), you will get a NullPointerException
        assertNotNull(e);
    }
}
