/**
 *
 * $Id$
 */
package org.eclipse.viatra.cep.core.metamodels.automaton.validation;

import org.eclipse.emf.common.util.EList;

import org.eclipse.viatra.cep.core.metamodels.automaton.EventToken;
import org.eclipse.viatra.cep.core.metamodels.automaton.Transition;

import org.eclipse.viatra.cep.core.metamodels.events.Event;

/**
 * A sample validator interface for {@link org.eclipse.viatra.cep.core.metamodels.automaton.State}.
 * This doesn't really do anything, and it's not a real EMF artifact.
 * It was generated by the org.eclipse.emf.examples.generator.validator plug-in to illustrate how EMF's code generator can be extended.
 * This can be disabled with -vmargs -Dorg.eclipse.emf.examples.generator.validator=false.
 */
public interface StateValidator {
    boolean validate();

    boolean validateInTransitions(EList<Transition> value);
    boolean validateOutTransitions(EList<Transition> value);
    boolean validateLabel(String value);
    boolean validateEventTokens(EList<EventToken> value);
    boolean validateLastProcessedEvent(Event value);
}
