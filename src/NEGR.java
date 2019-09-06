import SeniorProject.Negotiation.Bid;
import SeniorProject.Negotiation.NegotiationAgent;
import SeniorProject.Negotiation.NegotiationSession;
import SeniorProject.Negotiation.Negotiator;

import java.util.Random;

public class NEGR extends NegotiationAgent {
    private Random randomMachine = new Random();

    public double getBaseRatio(NegotiationSession session) {
        return (double) session.getTurn(this, session.getOwnerAgent()) / Negotiator.maximumMutualOffers;
    }

    public double getOfferRatio(NegotiationSession session) {
        return (Math.min(getBaseRatio(session) + randomMachine.nextDouble() * 0.1, 1)) * (getBidRanking().size() / 2);
    }

    @Override
    public Bid handleOffer(NegotiationSession negotiationSession, Bid bid) {
        if (bid == null) {
            return getBidRanking().get(0);
        } else {
            return getBidRanking().get((int) getOfferRatio(negotiationSession));
        }
    }

    @Override
    public Bid handleOffer(NegotiationSession negotiationSession) {
        return handleOffer(negotiationSession, null);
    }

    @Override
    public boolean isAccepted(NegotiationSession negotiationSession, Bid bid) {
        double ratio = (double) getBidRanking().indexOf(bid) / getBidRanking().size();

        if (ratio <= getOfferRatio(negotiationSession))
            return true;

        if (randomMachine.nextDouble() < 0.1 && ratio <= getBaseRatio(negotiationSession) * getBidRanking().size() * 3 / 5)
            return true;

        return false;
    }
}