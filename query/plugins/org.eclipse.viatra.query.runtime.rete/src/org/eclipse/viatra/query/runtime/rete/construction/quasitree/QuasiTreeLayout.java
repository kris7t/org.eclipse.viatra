/*******************************************************************************
 * Copyright (c) 2004-2010 Gabor Bergmann and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gabor Bergmann - initial API and implementation
 *******************************************************************************/

package org.eclipse.viatra.query.runtime.rete.construction.quasitree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.viatra.query.runtime.matchers.backend.IQueryBackendHintProvider;
import org.eclipse.viatra.query.runtime.matchers.backend.QueryEvaluationHint;
import org.eclipse.viatra.query.runtime.matchers.context.IQueryMetaContext;
import org.eclipse.viatra.query.runtime.matchers.planning.IQueryPlannerStrategy;
import org.eclipse.viatra.query.runtime.matchers.planning.QueryProcessingException;
import org.eclipse.viatra.query.runtime.matchers.planning.SubPlan;
import org.eclipse.viatra.query.runtime.matchers.planning.SubPlanFactory;
import org.eclipse.viatra.query.runtime.matchers.planning.helpers.BuildHelper;
import org.eclipse.viatra.query.runtime.matchers.planning.operations.PApply;
import org.eclipse.viatra.query.runtime.matchers.planning.operations.PEnumerate;
import org.eclipse.viatra.query.runtime.matchers.planning.operations.PJoin;
import org.eclipse.viatra.query.runtime.matchers.planning.operations.PProject;
import org.eclipse.viatra.query.runtime.matchers.planning.operations.PStart;
import org.eclipse.viatra.query.runtime.matchers.psystem.DeferredPConstraint;
import org.eclipse.viatra.query.runtime.matchers.psystem.EnumerablePConstraint;
import org.eclipse.viatra.query.runtime.matchers.psystem.PBody;
import org.eclipse.viatra.query.runtime.matchers.psystem.basicenumerables.ConstantValue;
import org.eclipse.viatra.query.runtime.matchers.psystem.queries.PQuery;
import org.eclipse.viatra.query.runtime.rete.construction.RetePatternBuildException;
import org.eclipse.viatra.query.runtime.rete.util.Options;
import org.eclipse.viatra.query.runtime.rete.util.ReteHintOptions;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * Layout ideas: see https://bugs.eclipse.org/bugs/show_bug.cgi?id=398763
 * 
 * @author Gabor Bergmann
 * 
 */
public class QuasiTreeLayout implements IQueryPlannerStrategy {

    private IQueryBackendHintProvider hintProvider;

    /**
     * @param hintProvider
     */
    public QuasiTreeLayout(IQueryBackendHintProvider hintProvider) {
        this.hintProvider = hintProvider;
    }

    @Override
    public SubPlan plan(PBody pSystem, Logger logger, IQueryMetaContext context)
            throws QueryProcessingException {
        return new Scaffold(pSystem, logger, context).run();
    }

	public class Scaffold {
        PBody pSystem;
        PQuery query;
        IQueryMetaContext context;
        private QueryEvaluationHint hints;
        //IOperationCompiler compiler;
        //SubPlanProcessor planProcessor = new SubPlanProcessor();
        SubPlanFactory planFactory;

        Set<DeferredPConstraint> deferredConstraints = null;
        Set<EnumerablePConstraint> enumerableConstraints = null;
        Set<ConstantValue> constantConstraints = null;
        Set<SubPlan> forefront = new LinkedHashSet<SubPlan>();
		Logger logger;

        Scaffold(PBody pSystem, Logger logger, /*IOperationCompiler compiler,*/ IQueryMetaContext context) {
            this.pSystem = pSystem;
			this.logger = logger;
            this.context = context;
            this.planFactory = new SubPlanFactory(pSystem);
            query = pSystem.getPattern();
            //this.compiler = compiler;
            //planProcessor.setCompiler(compiler);
            
            hints = hintProvider.getQueryEvaluationHint(query);
        }

        public SubPlan run() throws QueryProcessingException {
            try {
                logger.debug(String.format(
                		"%s: patternbody build started for %s",
                		getClass().getSimpleName(), 
                		query.getFullyQualifiedName()));

                // PROCESS CONSTRAINTS
                deferredConstraints = pSystem.getConstraintsOfType(DeferredPConstraint.class);
                enumerableConstraints = pSystem.getConstraintsOfType(EnumerablePConstraint.class);
                constantConstraints = pSystem.getConstraintsOfType(ConstantValue.class);
                
                for (EnumerablePConstraint enumerable : enumerableConstraints) {
                    SubPlan plan = planFactory.createSubPlan(new PEnumerate(enumerable));
                    admitSubPlan(plan);
                }
                if (enumerableConstraints.isEmpty()) { // EXTREME CASE
                    SubPlan plan = planFactory.createSubPlan(new PStart());
                    admitSubPlan(plan);
                }

                // JOIN FOREFRONT PLANS WHILE POSSIBLE
                while (forefront.size() > 1) {
                    // TODO QUASI-TREE TRIVIAL JOINS?

                    List<JoinCandidate> candidates = generateJoinCandidates();
                    JoinOrderingHeuristics ordering = new JoinOrderingHeuristics();
                    JoinCandidate selectedJoin = Collections.min(candidates, ordering);
                    doJoin(selectedJoin);
                }
                assert (forefront.size() == 1);

                // PROJECT TO PARAMETERS
                SubPlan preFinalPlan = forefront.iterator().next();
                SubPlan finalPlan = planFactory.createSubPlan(new PProject(pSystem.getSymbolicParameterVariables()), preFinalPlan);
                
                // FINAL CHECK, whether all exported variables are present + all constraint satisfied
                BuildHelper.finalCheck(pSystem, finalPlan, context);
    			// TODO integrate the check above in SubPlan / POperation 

                logger.debug(String.format(
                		"%s: patternbody query plan concluded for %s as: %s",
                		getClass().getSimpleName(), 
                		query.getFullyQualifiedName(),
                		finalPlan.toLongString()));
               return finalPlan;
            } catch (RetePatternBuildException ex) {
                ex.setPatternDescription(query);
                throw ex;
            }
        }

        public List<JoinCandidate> generateJoinCandidates() {
            List<JoinCandidate> candidates = new ArrayList<JoinCandidate>();
            int bIndex = 0;
            for (SubPlan b : forefront) {
                int aIndex = 0;
                for (SubPlan a : forefront) {
                    if (aIndex++ >= bIndex)
                        break;
                    candidates.add(new JoinCandidate(a, b, context));
                }
                bIndex++;
            }
            return candidates;
        }

        private void admitSubPlan(SubPlan plan) throws QueryProcessingException {
            // are there any unapplied constant filters that we can apply here?
            if (ReteHintOptions.prioritizeConstantFiltering.getValueOrDefault(hints)) {
                SetView<ConstantValue> unappliedConstants = 
                        Sets.difference(constantConstraints, plan.getAllEnforcedConstraints());
                for (ConstantValue constantConstraint : unappliedConstants) {
                    if (plan.getVisibleVariables().containsAll(constantConstraint.getAffectedVariables())) {
                        plan = planFactory.createSubPlan(new PApply(constantConstraint), plan);
                    }                    
                }
            }
        	// are there any variables that will not be needed anymore and are worth trimming?
        	// (check only if there are unenforced enumerables, so that there are still upcoming joins)
        	if (Options.planTrimOption != Options.PlanTrimOption.OFF &&
        			!plan.getAllEnforcedConstraints().containsAll(enumerableConstraints)) {
        		final SubPlan trimmed = BuildHelper.trimUnneccessaryVariables(planFactory, plan, true, context);
				plan = trimmed;
        	}        	
        	// are there any checkable constraints?
            for (DeferredPConstraint deferred : deferredConstraints) {
                if (!plan.getAllEnforcedConstraints().contains(deferred)) {
                    if (deferred.isReadyAt(plan, context)) {
                        admitSubPlan(planFactory.createSubPlan(new PApply(deferred), plan));
                        return;
                    }
                }
            }
            // if no checkable constraints and no unused variables
            forefront.add(plan);
        }

        private void doJoin(JoinCandidate selectedJoin)
                throws QueryProcessingException {
            forefront.remove(selectedJoin.getPrimary());
            forefront.remove(selectedJoin.getSecondary());
            admitSubPlan(selectedJoin.getJoinedPlan(planFactory));
        }

    }

}
