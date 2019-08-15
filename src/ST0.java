import SeniorProject.*;
import SeniorProject.Actions.*;
import SeniorProject.Negotiation.Bid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class ST0 extends AI {
    private Random randomMachine = new Random();
    private HashMap<StructureType, Double[]> locScores;
    private HashMap<ResourceType, Double> resourceWeights = new HashMap<>();
    private int sequentialSkips = 0;

    // Initialization of action categories
    private ArrayList<MoveRobber> actions_robber = new ArrayList<>();
    private ArrayList<CreateSettlement> actions_settlement = new ArrayList<>();
    private ArrayList<CreateRoad> actions_road = new ArrayList<>();
    private ArrayList<UpgradeSettlement> actions_upgrade = new ArrayList<>();
    private ArrayList<TradeWithBank> tradeBank_actions = new ArrayList<>();
    private ArrayList<DrawDevelopmentCard> actions_drawCard = new ArrayList<>();

    @Override
    public ArrayList<IAction> createActions(boolean isInitial) {
        // UpgradeSettlement - CreateSettlement comparison
        // Cannot get any points from impossible Settlement Actions.
        // Creating multiple possible actions will have bonus score.
        // Calculate the score with thinking of the achieved goal (for resources).

        initialization();

        /// region If robber must be moved, move the robber.
        if (actions_robber.size() > 0) {
            MoveRobber bestRobberAction = null;
            int bestRobberAction_score = Integer.MIN_VALUE;
            for (MoveRobber robberAction : actions_robber) {
                ArrayList<Building> allBuildings = getVirtualBoard().getLands().get(robberAction.getLandIndex()).getAllBuildings();
                ArrayList<Building> myBuildings = new ArrayList<>();
                for (Building building : allBuildings)
                    if (building.getPlayer().getIndex() == getOwner().getIndex())
                        myBuildings.add(building);

                int score = (int) (robberAction.getVictimIndex() != -1 ? Math.pow(getVirtualBoard().getPlayers().get(robberAction.getVictimIndex()).getVictoryPoint(), 2) : 0);
                score -= (int) Math.pow(myBuildings.size(), 2);
                score += (int) Math.pow(allBuildings.size() - myBuildings.size(), 2);

                if (score > bestRobberAction_score) {
                    bestRobberAction_score = score;
                    bestRobberAction = robberAction;
                }
            }

            doAction(bestRobberAction);
        }
        /// endregion

        if (tradeBank_actions.size() > 0) {
            System.out.println("I can do TradeWithBank(s).");

            ArrayList<ArrayList<? extends IAction>> action_categories = new ArrayList<>();
            action_categories.add(actions_settlement);
            action_categories.add(actions_upgrade);
            action_categories.add(actions_drawCard);
            action_categories.add(actions_road);

            ArrayList<Resource> action_category_costs = new ArrayList<>();
            action_category_costs.add(CreateSettlement.COST);
            action_category_costs.add(UpgradeSettlement.COST);
            action_category_costs.add(DrawDevelopmentCard.COST);
            action_category_costs.add(CreateRoad.COST);

            ArrayList<Action> actions_category_enums = new ArrayList<>();
            actions_category_enums.add(Action.CreateSettlement);
            actions_category_enums.add(Action.UpgradeSettlement);
            actions_category_enums.add(Action.DrawDevCard);
            actions_category_enums.add(Action.CreateRoad);

            ArrayList<ArrayList<TradeWithBank>> allSolvingActions = new ArrayList<>();

            for (int i = 0; i < action_categories.size(); i++) {
                ArrayList actions_group = action_categories.get(i);
                Resource actions_cost = action_category_costs.get(i);

                if (actions_group.size() == 0) {
                    System.out.println("I cannot do any " + actions_category_enums.get(i) + "(s).");

                    allSolvingActions.add(new ArrayList<>());

                    Resource remainingResources = new Resource(getOwner().getResource());
                    remainingResources.disjoin(actions_cost);

                    if (-remainingResources.getNegatives().getSum() <= remainingResources.getPositives().getSum()) {
                        for (TradeWithBank tradeWithBank : tradeBank_actions) {
                            Resource missingResources_checkPoint = new Resource(remainingResources);

                            remainingResources.join(tradeWithBank.getTakenResources());
                            remainingResources.disjoin(tradeWithBank.getGivenResources());

                            if (remainingResources.getNegatives().getSum() == 0) {
                                allSolvingActions.get(i).add(tradeWithBank);
                                System.out.println("I can fix this problem with " + tradeWithBank);
                            }

                            remainingResources = missingResources_checkPoint;
                        }
                    }
                }
                else {
                    allSolvingActions.add(new ArrayList<>());
                }
            }

            double maxScore = Integer.MIN_VALUE;
            TradeWithBank maxScore_owner = null;
            for (int i = 0; i < allSolvingActions.size(); i++) {
                ArrayList<TradeWithBank> solving_tradeBanks = allSolvingActions.get(i);
                Action action = actions_category_enums.get(i);

                if (solving_tradeBanks.size() != 0)
                    System.out.println(action + " solver's scores: ");

                for (int j = 0; j < solving_tradeBanks.size(); j++) {
                    TradeWithBank tradeWithBank = solving_tradeBanks.get(j);

                    double score = 0;

                    score += (action == Action.CreateSettlement) ? 1 : 0;
                    score += (action == Action.UpgradeSettlement) ? 1 : 0;

                    for (ResourceType resourceType : ResourceType.values())
                        score -= tradeWithBank.getGivenResources().get(resourceType) * resourceWeights.get(resourceType);

                    if (score > maxScore) {
                        maxScore = score;
                        maxScore_owner = tradeWithBank;
                    }

                    System.out.println(tradeWithBank + " Score: " + score);
                }
            }

            if (maxScore_owner != null && maxScore > -1) {
                System.out.println("I chose " + maxScore_owner);
                doAction(maxScore_owner);
            }
        }

        while (true) {
            boolean roadAdded = false;
            boolean settlementAdded = false;

            //region While a settlement can be built, build it.
            Double[] _locScores = locScores.get(SETTLEMENT);
            makeAllPositive(_locScores);

            while (actions_settlement.size() > 0) {
                // Mark all of them as impossible
                for (int i = 0; i < _locScores.length; i++)
                    _locScores[i] *= -1;

                // Make the possible ones active again
                for (CreateSettlement createSettlement : actions_settlement)
                    _locScores[createSettlement.getLocation().getIndex()] *= -1;

                // Choose the location has the maximum score
                Location bestLocation = null;
                for (int i = 0; i < _locScores.length; i++) {
                    if (bestLocation == null || _locScores[i] > _locScores[bestLocation.getIndex()])
                        bestLocation = getVirtualBoard().getLocations().get(i);
                }

                // Create the settlement on this location
                for (CreateSettlement createSettlement : actions_settlement) {
                    if (createSettlement.getLocation().equals(bestLocation)) {
                        doAction(createSettlement);
                        settlementAdded = true;
                        break;
                    }
                }
            }
            ///endregion

            //region If a good road can be built, build it.
            _locScores = locScores.get(SETTLEMENT);
            makeAllPositive(_locScores);

            if (actions_road.size() > 0) {
                // Mark the adjacent locations of settlements as impossible.
                for (Location location : getVirtualBoard().getLocations()) {
                    if (location.getOwner() != null) {
                        _locScores[location.getIndex()] *= -1;

                        for (Location adjacentLocation : location.getAdjacentLocations())
                            _locScores[adjacentLocation.getIndex()] *= -1;
                    }
                }

                // Find the best three locations.
                Location[] best3Locations = new Location[3];
                double[] best3Locations_score = new double[3];
                for (int i = 0; i < _locScores.length; i++) {
                    for (int j = 2; j >= 0; j--) {
                        if (_locScores[i] > 0 && (best3Locations[j] == null || _locScores[i] > best3Locations_score[j])) {
                            for (int k = best3Locations.length - 1; k >= j; k--) {
                                if (k == 2) {
                                    best3Locations[k] = null;
                                    best3Locations_score[k] = -Double.MAX_VALUE;
                                } else {
                                    best3Locations[k + 1] = best3Locations[k];
                                    best3Locations_score[k + 1] = best3Locations_score[k];
                                }
                            }

                            best3Locations[j] = getVirtualBoard().getLocations().get(i);
                            best3Locations_score[j] = _locScores[i];
                            break;
                        }
                    }
                }

                // Set the reachable locations via possible roads
                ArrayList<Location> actionRoad_locations = new ArrayList<>();
                for (CreateRoad createRoad : actions_road) {
                    if (!actionRoad_locations.contains(createRoad.getLocations()[0]))
                        actionRoad_locations.add(createRoad.getLocations()[0]);

                    if (!actionRoad_locations.contains(createRoad.getLocations()[1]))
                        actionRoad_locations.add(createRoad.getLocations()[1]);
                }

                // Find the best three paths and their discounted lengths (according to the best three locations) using the shortest path.
                ArrayList<ArrayList<Road>> threeBestPaths = new ArrayList<>();
                ArrayList<Integer> threeBestPaths_discounted_len = new ArrayList<>();
                for (int i = 0; i < best3Locations.length; i++) {
                    if (best3Locations[i] != null) {
                        ArrayList<Road> bestPath = new ArrayList<>();
                        int bestPath_discounted_len = Integer.MAX_VALUE;

                        for (Location location : actionRoad_locations) {
                            if (location.getOwner() != null && location.getOwner().getIndex() == getOwner().getIndex()) {
                                ArrayList<Road> path = getShortestPath(location, best3Locations[i], false).getValue();

                                boolean ignorePath = true;
                                for (CreateRoad createRoad : actions_road) {
                                    if (path.contains(createRoad.getRoad())) {
                                        ignorePath = false;
                                        break;
                                    }
                                }

                                if (ignorePath)
                                    continue;

                                int path_discounted_len = path.size();

                                for (Road _road : path)
                                    path_discounted_len -= getOwner().getStructures().contains(_road) ? 1 : 0;

                                if (path_discounted_len < bestPath_discounted_len) {
                                    bestPath = path;
                                    bestPath_discounted_len = path_discounted_len;
                                }
                            }
                        }

                        threeBestPaths.add(bestPath);
                        threeBestPaths_discounted_len.add(bestPath_discounted_len);
                    } else {
                        threeBestPaths.add(null);
                        threeBestPaths_discounted_len.add(null);
                    }
                }

                /// region DEBUG PRINT
                /*for (int i = 0; i < threeBestPaths.size(); i++) {
                    System.out.println("    Path(" + i + "): " + threeBestPaths.get(i));
                    System.out.println("    Path(" + i + ") Discounted Length: " + threeBestPaths_discounted_len.get(i));
                }*/
                /// endregion

                // Set the scores of the best three paths.
                ArrayList<Double> threeBestPaths_scores = new ArrayList<>();

                for (int i = 0; i < 3; i++) {
                    if (threeBestPaths.get(i) != null && threeBestPaths.get(i).size() > 0) {
                        /// region DEBUG PRINT
                        /*System.out.println(-threeBestPaths_discounted_len.get(0));
                        System.out.println(_locScores[threeBestPaths.get(0).get(threeBestPaths.get(0).size() - 1).getEndLocation().getIndex()]);*/
                        /// endregion

                        threeBestPaths_scores.add((double) -threeBestPaths_discounted_len.get(i)
                                + _locScores[threeBestPaths.get(i).get(threeBestPaths.get(i).size() - 1).getEndLocation().getIndex()]);
                    } else
                        threeBestPaths_scores.add(-Double.MAX_VALUE);
                }

                /// region DEBUG PRINT
                /*for (int i = 0; i < threeBestPaths.size(); i++)
                    System.out.println("    Path(" + i + ") Score: " + threeBestPaths_scores.get(i));*/
                /// endregion

                // Choose the best path
                ArrayList<Road> chosenPath = new ArrayList<>();
                Double chosenPath_score = -Double.MAX_VALUE;
                for (int i = 0; i < threeBestPaths.size(); i++) {
                    ArrayList<Road> path = threeBestPaths.get(i);

                    if (path != null && path.size() != 0 && threeBestPaths_scores.get(i) > chosenPath_score) {
                        chosenPath_score = threeBestPaths_scores.get(i);
                        chosenPath = path;
                    }
                }

                /// region DEBUG PRINT
                /*System.out.println("Ch. Path: " + chosenPath);
                System.out.println("Ch. Path Sc.: " + chosenPath_score);*/
                /// endregion

                // Create roads in order to complete the best path.
                for (Road road : chosenPath) {
                    int[] locationIndexes = new int[2];
                    locationIndexes[0] = road.getStartLocation().getIndex();
                    locationIndexes[1] = road.getEndLocation().getIndex();

                    int index = actions_road.indexOf(new CreateRoad(locationIndexes, getOwner().getIndex(), getVirtualBoard()));
                    if (index != -1) {
                        doAction(actions_road.get(index));
                        roadAdded = true;

                        continue;
                    }
                }
            }
            ///endregion

            if (!roadAdded && !settlementAdded)
                break;
        }

        /// region Do all the DrawDevelopmentCard(s)
        while (actions_drawCard.size() > 0)
            doAction(actions_drawCard.get(0));

        ///region Do UpgradeSettlement(s) randomly.
        while (actions_upgrade.size() > 0) {
            IAction action = actions_upgrade.get(randomMachine.nextInt(actions_upgrade.size()));
            doAction(action);
        }
        ///endregion

        if (getActionsDone().size() == 0) {
            // If the AI has skipped too many turns,
            if (skippedTooMuch()) {
                System.out.println(getOwner() + "'s " + sequentialSkips + ". pass.");

                // Do actions that are not TradeWithBank(s) randomly.
                while (getPossibleActions().size() > tradeBank_actions.size()) {
                    IAction action = getPossibleActions().get(randomMachine.nextInt(getPossibleActions().size()));

                    if (action instanceof TradeWithBank)
                        continue;

                    doAction(action);
                }
            }

            sequentialSkips++;
        } else
            sequentialSkips = 0;

        return getActionsDone();
    }

    @Override
    public double calculateBidUtility(Bid bid) {
        double utility = 0;

        for (ResourceType resourceType : ResourceType.values())
            utility += resourceWeights.get(resourceType) * bid.getChange().get(resourceType);

        return utility;
    }

    @Override
    public void updateBidRanking() {
        updateResourceWeights();
        System.out.println("    " + getOwner() + "'s Resource Weights: " + resourceWeights);

        Resource desiredResource;
        Action desiredAction;
        clearBidRanking();

        for (int i = 0; i < Action.values().length; i++) {
            desiredResource = new Resource(getOwner().getResource());
            desiredAction = Action.values()[i];

            // Ignore the ineffective action
            if (desiredAction == Action.ZeroCost)
                continue;

            if (desiredAction == Action.CreateRoad) {
                desiredResource.disjoin(Road.COST);
                if (desiredResource.getPositives().getSum() > 0)
                    resourceAnalyze(desiredResource);
            } else if (desiredAction == Action.CreateSettlement) {
                desiredResource.disjoin(Settlement.COST);
                if (desiredResource.getPositives().getSum() >= 0) {
                    resourceAnalyze(desiredResource);
                }
            } else if (desiredAction == Action.UpgradeSettlement) {
                desiredResource.disjoin(City.COST);
                if (desiredResource.getPositives().getSum() >= 0)
                    resourceAnalyze(desiredResource);
            } else if (desiredAction == Action.DrawDevCard) {
                desiredResource.disjoin(DrawDevelopmentCard.COST);
                if (desiredResource.getPositives().getSum() >= 0)
                    resourceAnalyze(desiredResource);
            }
        }
    }

    private void resourceAnalyze(Resource desiredResource) {
        Resource wantedResource = new Resource();
        Resource freeResource = new Resource(desiredResource);

        for (ResourceType type : freeResource.keySet()) {
            if (freeResource.get(type) < 0) {
                wantedResource.add(type, -freeResource.get(type));
                freeResource.replace(type, 0);
            }
        }

        for (ResourceType type : wantedResource.keySet()) {
            if (wantedResource.get(type) > 0)
                createBids(type, wantedResource.get(type), freeResource);
        }
    }

    private void createBids(ResourceType type, int need, Resource freeResource) {
        if (need > 1)
            createBids(type, need - 1, freeResource);

        Resource bid;
        for (ResourceType givenType : freeResource.keySet()) {
            if (freeResource.get(givenType) > 0) {
                bid = new Resource();
                bid.add(type, need);

                ArrayList<ResourceType> freeTypeList = new ArrayList<>();
                for (int i = 0; i < ResourceType.values().length; i++) {
                    if (ResourceType.values()[i] != type)
                        freeTypeList.add(ResourceType.values()[i]);
                }

                int[] max = new int[4];
                for (int i = 0; i < max.length; i++)
                    max[i] = freeTypeList.size() > 0 ? Math.min(freeResource.get(freeTypeList.get(i)), need + 2) : 0;

                int[] givenCount = new int[4];
                for (givenCount[0] = max[0]; givenCount[0] >= 0; givenCount[0]--) {
                    for (givenCount[1] = Math.min(max[1], need + 2 - givenCount[0]); givenCount[1] >= 0; givenCount[1]--) {
                        for (givenCount[2] = Math.min(max[2], need + 2 - givenCount[0] - givenCount[1]); givenCount[2] >= 0; givenCount[2]--) {
                            for (givenCount[3] = Math.min(max[3], need + 2 - givenCount[0] - givenCount[1] - givenCount[2]); givenCount[3] >= 0; givenCount[3]--) {
                                Resource bidBefore = new Resource(bid);

                                for (int i = 0; i < freeTypeList.size() - 1; i++)
                                    bid.add(freeTypeList.get(i), -givenCount[i]);

                                // ?
                                // At least one resourceType must be negative.
                                boolean isValid = false;
                                for (ResourceType resourceType : ResourceType.values()) {
                                    if (bid.get(resourceType) < 0) {
                                        isValid = true;
                                        break;
                                    }
                                }

                                // ?
                                Bid addedBid = new Bid(bid);
                                if (isValid && !getBidRanking().contains(addedBid))
                                    addBidToBidRanking(addedBid);

                                bid = bidBefore;
                            }
                        }
                    }
                }
            }
        }
    }

    private void initialization() {
        locScores = new HashMap<>();

        locScores.put(SETTLEMENT, new Double[getBoard().getLocations().size()]);

        updateLocationScores_for(SETTLEMENT);
        updatePossibleActions();
        updateResourceWeights(); // ?
    }

    private void doAction(IAction action) {
        doVirtually(action);

        updateLocationScores_for(SETTLEMENT);
        updatePossibleActions();
    }

    private void updateResourceWeights() {
        System.out.println("    TRIGGERED");

        State.StateBuilder stateBuilder = new State.StateBuilder(Board.deepCopy(getBoard()));
        State state = stateBuilder.build();
        ArrayList<IAction> possibleActions = state.getPossibleActions(getOwner().getIndex());

        int createSettlement_size = Global.getActions_of(possibleActions, CreateSettlement.class).size();
        int createRoad_size = Global.getActions_of(possibleActions, CreateRoad.class).size();
        int upgradeSettlement_size = Global.getActions_of(possibleActions, UpgradeSettlement.class).size();
        int drawDevelopmentCard_size = Global.getActions_of(possibleActions, DrawDevelopmentCard.class).size();

        for (ResourceType resourceType : ResourceType.values())
            resourceWeights.put(resourceType, 0.0);

        HashMap<Action, Integer> missingResources = new HashMap<>();

        int settlement_missing = 0;
        Resource missingResourceFor_settlement = new Resource(Settlement.COST);
        missingResourceFor_settlement.disjoin(getOwner().getResource());
        for (ResourceType resourceType : ResourceType.values()) {
            if ((resourceType.equals(ResourceType.BRICK) || resourceType.equals(ResourceType.LUMBER)
                    || resourceType.equals(ResourceType.WOOL) || resourceType.equals(ResourceType.GRAIN))
                    && missingResourceFor_settlement.get(resourceType) > 0)
                settlement_missing += missingResourceFor_settlement.get(resourceType);
        }
        missingResources.put(Action.CreateSettlement, settlement_missing);

        int road_missing = 0;
        Resource missingResourceFor_road = new Resource(Road.COST);
        missingResourceFor_road.disjoin(getOwner().getResource());
        for (ResourceType resourceType : ResourceType.values()) {
            if ((resourceType.equals(ResourceType.BRICK) || resourceType.equals(ResourceType.LUMBER)) && missingResourceFor_road.get(resourceType) > 0)
                road_missing -= missingResourceFor_road.get(resourceType);
        }
        missingResources.put(Action.CreateRoad, road_missing);

        int city_missing = 0;
        Resource missingResourceFor_city = new Resource(City.COST);
        missingResourceFor_city.disjoin(getOwner().getResource());
        for (ResourceType resourceType : ResourceType.values()) {
            if ((resourceType.equals(ResourceType.GRAIN) || resourceType.equals(ResourceType.ORE)) && missingResourceFor_city.get(resourceType) > 0)
                city_missing -= missingResourceFor_city.get(resourceType);
        }
        missingResources.put(Action.UpgradeSettlement, city_missing);

        int draw_missing = 0;
        Resource missingResourceFor_draw = new Resource(DrawDevelopmentCard.COST);
        missingResourceFor_draw.disjoin(getOwner().getResource());
        for (ResourceType resourceType : ResourceType.values()) {
            if ((resourceType.equals(ResourceType.ORE) || resourceType.equals(ResourceType.GRAIN) || resourceType.equals(ResourceType.WOOL)) && missingResourceFor_draw.get(resourceType) > 0)
                draw_missing -= missingResourceFor_draw.get(resourceType);
        }
        missingResources.put(Action.DrawDevCard, draw_missing);

        System.out.print("    " + getOwner() + "'s Negotiation Calculations:");

        Action chosenAction = null;
        int chosenAction_score = Integer.MIN_VALUE;
        for (Action actionType : missingResources.keySet()) {
            int score = -missingResources.get(actionType) * 10;
            score += actionType.equals(Action.CreateSettlement) ? 15 : 0;
            score -= missingResources.get(actionType) == 0 ? 75 : 0;

            if (actionType.equals(Action.CreateSettlement)) {
                if (state.getVictoryPoints(getOwner().getIndex()) == Global.MAX_VICTORY_POINTS - 1)
                    score += 50;
            } else if (actionType.equals(Action.UpgradeSettlement)) {
                if (state.getVictoryPoints(getOwner().getIndex()) == Global.MAX_VICTORY_POINTS - 1)
                    score += 50;
            } else if (actionType.equals(Action.DrawDevCard)) {
                if (state.getVictoryPoints(getOwner().getIndex()) == Global.MAX_VICTORY_POINTS - 1)
                    score += 50;

                if (state.getVictoryPoints(getOwner().getIndex()) == Global.MAX_VICTORY_POINTS - 1 /*&&
                    state.getKnights(getOwner().getIndex()) - state.getKnights_max() <= 1*/)
                    score += 50;
            } else if (actionType.equals(Action.CreateRoad)) {
                int longestRoad_length = 0;
                int myLongestRoad = 0;
                int longestRoad_repeat = 0;
                for (Player player : getBoard().getPlayers()) {
                    int length = state.getLongestRoad_lengths().get(player.getIndex());
                    if (length > longestRoad_length) {
                        longestRoad_length = length;
                        longestRoad_repeat = 1;
                    } else if (length == longestRoad_length)
                        longestRoad_repeat++;

                    if (player.getIndex() == getOwner().getIndex())
                        myLongestRoad = length;
                }

                if (myLongestRoad == longestRoad_length && longestRoad_length > 3)
                    score += (longestRoad_repeat - 1) * 60;
            }

            if (score > chosenAction_score) {
                chosenAction_score = score;
                chosenAction = actionType;
            }

            System.out.print("  " + actionType + "'s score: " + score);
        }

        if (chosenAction == Action.CreateRoad) {
            resourceWeights.put(BRICK, 0.3);
            resourceWeights.put(LUMBER, 0.3);
            resourceWeights.put(ORE, 0.08);
            resourceWeights.put(GRAIN, 0.16);
            resourceWeights.put(WOOL, 0.16);
        } else if (chosenAction == Action.CreateSettlement) {
            resourceWeights.put(BRICK, 0.22);
            resourceWeights.put(LUMBER, 0.22);
            resourceWeights.put(ORE, 0.12);
            resourceWeights.put(GRAIN, 0.22);
            resourceWeights.put(WOOL, 0.22);
        } else if (chosenAction == Action.UpgradeSettlement) {
            resourceWeights.put(BRICK, 0.25 / 3);
            resourceWeights.put(LUMBER, 0.25 / 3);
            resourceWeights.put(ORE, 0.3);
            resourceWeights.put(GRAIN, 0.45);
            resourceWeights.put(WOOL, 0.25 / 3);
        } else if (chosenAction == Action.DrawDevCard) {
            resourceWeights.put(BRICK, 0.13 / 2);
            resourceWeights.put(LUMBER, 0.13 / 2);
            resourceWeights.put(ORE, 0.29);
            resourceWeights.put(GRAIN, 0.29);
            resourceWeights.put(WOOL, 0.29);
        }

        System.out.println("   [" + getOwner() + " wants to " + chosenAction + "]");
    }

    private void updateLocationScores_for(StructureType structureType) {
        Double[] _locScores = locScores.get(structureType);
        HashMap<ResourceType, Double> _resourceWeights;

        // For the first turns
        if (getVirtualBoard().getTurn() < 5) {
            _resourceWeights = new HashMap<>();
            _resourceWeights.put(BRICK, 1.0 / 3);
            _resourceWeights.put(LUMBER, 1.0 / 3);
            _resourceWeights.put(ORE, 1.0 / 9);
            _resourceWeights.put(GRAIN, 1.0 / 9);
            _resourceWeights.put(WOOL, 1.0 / 9);
        } else
            _resourceWeights = resourceWeights;

        if (structureType == SETTLEMENT) {
            for (int i = 0; i < _locScores.length; i++) {
                Location location = getBoard().getLocations().get(i);

                _locScores[i] = 0.0;
                for (Land land : location.getAdjacentLands()) {
                    _locScores[i] += location.isActive() ? 0.01 : 0;
                    _locScores[i] += land.getDiceChance() * 36.0;

                    for (ResourceType resourceType : ResourceType.values())
                        _locScores[i] += land.getResourceType() == resourceType ? _resourceWeights.get(resourceType) * 10 : 0.0;
                }

                for (Road connectedRoads : location.getConnectedRoads()) {
                    if (connectedRoads.getPlayer().getIndex() != getOwner().getIndex()) {
                        _locScores[i] += getVirtualBoard().isValid(new Settlement(location, getOwner()), getVirtualBoard().isInitial()) ? 10 : 0;
                    }
                }
            }
        }
    }

    private void updatePossibleActions() {
        actions_robber = Global.getActions_of(getPossibleActions(), MoveRobber.class);
        actions_settlement = Global.getActions_of(getPossibleActions(), CreateSettlement.class);
        actions_road = Global.getActions_of(getPossibleActions(), CreateRoad.class);
        actions_upgrade = Global.getActions_of(getPossibleActions(), UpgradeSettlement.class);
        tradeBank_actions = Global.getActions_of(getPossibleActions(), TradeWithBank.class);
        actions_drawCard = Global.getActions_of(getPossibleActions(), DrawDevelopmentCard.class);
    }

    private boolean skippedTooMuch() {
        return sequentialSkips > 4;
    }

    private void makeAllPositive(Double[] array) {
        for (int i = 0; i < array.length; i++)
            array[i] = Math.abs(array[i]);
    }
}