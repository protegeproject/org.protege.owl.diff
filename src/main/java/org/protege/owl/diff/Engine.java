package org.protege.owl.diff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.protege.owl.diff.align.AlignmentAlgorithm;
import org.protege.owl.diff.align.OwlDiffMap;
import org.protege.owl.diff.align.impl.OwlDiffMapImpl;
import org.protege.owl.diff.align.util.PrioritizedComparator;
import org.protege.owl.diff.present.Changes;
import org.protege.owl.diff.present.EntityBasedDiff;
import org.protege.owl.diff.present.EntityBasedDiff.DiffType;
import org.protege.owl.diff.present.PresentationAlgorithm;
import org.protege.owl.diff.present.util.PresentationAlgorithmComparator;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Engine {
    private Logger logger = LoggerFactory.getLogger(Engine.class.getName());
    
    private OWLDataFactory factory;
    private OWLOntology ontology1;
    private OWLOntology ontology2;
    private Map<String, String> parameters;
    
    private OwlDiffMap diffMap;
    private List<AlignmentAlgorithm> diffAlgorithms = new ArrayList<>();

    private Changes changes;
    private List<PresentationAlgorithm> changeAlgorithms = new ArrayList<>();
    
    private Collection<Object> services = new ArrayList<>();

    
    public Engine(OWLOntology ontology1, 
                  OWLOntology ontology2) {
    	this.factory = ontology2.getOWLOntologyManager().getOWLDataFactory();
    	this.ontology1 = ontology1;
    	this.ontology2 = ontology2;
        this.parameters = new HashMap<>();
    }
    
    public OWLDataFactory getOWLDataFactory() {
    	return factory;
    }
    
    public OwlDiffMap getOwlDiffMap() {
	    return diffMap;
	}

	public Changes getChanges() {
		return changes;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}
	
	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}

    public void setAlignmentAlgorithms(AlignmentAlgorithm... algorithms) {
	    this.diffAlgorithms.clear();
	    for (AlignmentAlgorithm algorithm : algorithms) {
	    	this.diffAlgorithms.add(algorithm);
	    }
	    Collections.sort(diffAlgorithms, new PrioritizedComparator());
	}
    
    public Collection<AlignmentAlgorithm> getAlignmentAlgorithms() {
    	return Collections.unmodifiableList(diffAlgorithms);
    }

	public void setPresentationAlgorithms(PresentationAlgorithm... algorithms) {
		changeAlgorithms.clear();
		for (PresentationAlgorithm algorithm : algorithms) {
			changeAlgorithms.add(algorithm);
		}
		Collections.sort(changeAlgorithms, new PresentationAlgorithmComparator());
	}
	
	public Collection<PresentationAlgorithm> getPresentationAlgorithms() {
		return Collections.unmodifiableList(changeAlgorithms);
	}

	public void addService(Object o) {
		services.add(o);
	}

	public <X> X getService(Class<? extends X> implementing) {
		for (Object o : services) {
			if (implementing.isAssignableFrom(o.getClass())) {
				return implementing.cast(o);
			}
		}
		return null;
	}

	public void display() {
	    Collection<EntityBasedDiff> ediffs = changes.getEntityBasedDiffs();
	    for (EntityBasedDiff ediff : ediffs) {
	        if (ediff.getDiffType() != DiffType.EQUIVALENT) {
	            logger.info(ediff.getDescription());
	        }
	    }
	}

	public void phase1() {
    	phase1Init();
    	phase1Run();
        phase1Cleanup();
    }
    
    private void phase1Init() {
    	services.clear();
		diffMap = new OwlDiffMapImpl(factory, ontology1, ontology2);
		for (AlignmentAlgorithm algorithm : diffAlgorithms) {
			algorithm.initialize(this);
		}
	}
    
    private void phase1Run() {
        boolean progress;
        boolean finished = false;
        do {
            progress  = false;
            for (AlignmentAlgorithm da : diffAlgorithms) {
                int entitiesCount = diffMap.getUnmatchedSourceEntities().size();
                int individualsCount = diffMap.getUnmatchedSourceAnonymousIndividuals().size();
                if (entitiesCount == 0 && individualsCount == 0) {
                    finished = true;
                    break;
                }
                try {
                    da.run();
                 }
                catch (Exception e) {
                    logger.warn("Diff Algorithm " + da.getAlgorithmName() + "failed (" + e + ").  Continuing...");
                }
				progress = progress ||
                              (entitiesCount > diffMap.getUnmatchedSourceEntities().size()) ||
                              (individualsCount > diffMap.getUnmatchedSourceAnonymousIndividuals().size());
            }
        }
        while (progress && !finished);
        diffMap.finish();
    }

	private void phase1Cleanup() {
	    for (AlignmentAlgorithm algorithm : diffAlgorithms) {
	        try {
	            algorithm.reset();
	        }
	        catch (Exception t) {
	            logger.warn("Diff Algorithm " + algorithm.getAlgorithmName() + " wouldn't reset (" + t + ")");
	        }
		}
	}

	public void phase2() {
    	phase2Init();
    	phase2Run();
    }
    
    private void phase2Init() {
    	changes = new Changes(diffMap);
    	for (PresentationAlgorithm algorithm : changeAlgorithms) {
    		algorithm.initialise(this);
    	}
    }
    
    private void phase2Run() {
    	for (PresentationAlgorithm algorithm : changeAlgorithms) {
    		algorithm.apply();
    	}
    }
}
