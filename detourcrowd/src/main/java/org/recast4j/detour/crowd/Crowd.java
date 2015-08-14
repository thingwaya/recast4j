package org.recast4j.detour.crowd;

import static org.recast4j.detour.DetourCommon.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.VectorPtr;


/*
 * 
//
// Copyright (c) 2009-2010 Mikko Mononen memon@inside.org
//
// This software is provided 'as-is', without any express or implied
// warranty.  In no event will the authors be held liable for any damages
// arising from the use of this software.
// Permission is granted to anyone to use this software for any purpose,
// including commercial applications, and to alter it and redistribute it
// freely, subject to the following restrictions:
// 1. The origin of this software must not be misrepresented; you must not
//    claim that you wrote the original software. If you use this software
//    in a product, an acknowledgment in the product documentation would be
//    appreciated but is not required.
// 2. Altered source versions must be plainly marked as such, and must not be
//    misrepresented as being the original software.
// 3. This notice may not be removed or altered from any source distribution.
//

#ifndef DETOURCROWD_H
#define DETOURCROWD_H

#include "DetourNavMeshQuery.h"
#include "DetourObstacleAvoidance.h"
#include "DetourLocalBoundary.h"
#include "DetourPathCorridor.h"
#include "DetourProximityGrid.h"
#include "DetourPathQueue.h"


enum MoveRequestState
{
	DT_CROWDAGENT_TARGET_NONE = 0,
	DT_CROWDAGENT_TARGET_FAILED,
	DT_CROWDAGENT_TARGET_VALID,
	DT_CROWDAGENT_TARGET_REQUESTING,
	DT_CROWDAGENT_TARGET_WAITING_FOR_QUEUE,
	DT_CROWDAGENT_TARGET_WAITING_FOR_PATH,
	DT_CROWDAGENT_TARGET_VELOCITY,
};


struct dtCrowdAgentAnimation
{
	bool active;
	float initPos[3], startPos[3], endPos[3];
	dtPolyRef polyRef;
	float t, tmax;
};

/// Crowd agent update flags.
/// @ingroup crowd
/// @see dtCrowdAgentParams::updateFlags
enum UpdateFlags
{
	DT_CROWD_ANTICIPATE_TURNS = 1,
	DT_CROWD_OBSTACLE_AVOIDANCE = 2,
	DT_CROWD_SEPARATION = 4,
	DT_CROWD_OPTIMIZE_VIS = 8,			///< Use #dtPathCorridor::optimizePathVisibility() to optimize the agent path.
	DT_CROWD_OPTIMIZE_TOPO = 16,		///< Use dtPathCorridor::optimizePathTopology() to optimize the agent path.
};

struct dtCrowdAgentDebugInfo
{
	int idx;
	float optStart[3], optEnd[3];
	dtObstacleAvoidanceDebugData* vod;
};

/// Provides local steering behaviors for a group of agents. 
/// @ingroup crowd
class dtCrowd
{
	int m_maxAgents;
	dtCrowdAgent* m_agents;
	dtCrowdAgent** m_activeAgents;
	dtCrowdAgentAnimation* m_agentAnims;
	
	dtPathQueue m_pathq;

	dtObstacleAvoidanceParams m_obstacleQueryParams[DT_CROWD_MAX_OBSTAVOIDANCE_PARAMS];
	dtObstacleAvoidanceQuery* m_obstacleQuery;
	
	dtProximityGrid* m_grid;
	
	dtPolyRef* m_pathResult;
	int m_maxPathResult;
	
	float m_ext[3];

	dtQueryFilter m_filters[DT_CROWD_MAX_QUERY_FILTER_TYPE];

	float m_maxAgentRadius;

	int m_velocitySampleCount;

	dtNavMeshQuery* m_navquery;

	void updateTopologyOptimization(dtCrowdAgent** agents, const int nagents, const float dt);
	void updateMoveRequest(const float dt);
	void checkPathValidity(dtCrowdAgent** agents, const int nagents, const float dt);

	inline int getAgentIndex(const dtCrowdAgent* agent) const  { return (int)(agent - m_agents); }

	bool requestMoveTargetReplan(const int idx, dtPolyRef ref, const float* pos);

	void purge();
	
public:
	dtCrowd();
	~dtCrowd();
	
	/// Initializes the crowd.  
	///  @param[in]		maxAgents		The maximum number of agents the crowd can manage. [Limit: >= 1]
	///  @param[in]		maxAgentRadius	The maximum radius of any agent that will be added to the crowd. [Limit: > 0]
	///  @param[in]		nav				The navigation mesh to use for planning.
	/// @return True if the initialization succeeded.
	bool init(const int maxAgents, const float maxAgentRadius, dtNavMesh* nav);
	
	/// Sets the shared avoidance configuration for the specified index.
	///  @param[in]		idx		The index. [Limits: 0 <= value < #DT_CROWD_MAX_OBSTAVOIDANCE_PARAMS]
	///  @param[in]		params	The new configuration.
	void setObstacleAvoidanceParams(const int idx, const dtObstacleAvoidanceParams* params);

	/// Gets the shared avoidance configuration for the specified index.
	///  @param[in]		idx		The index of the configuration to retreive. 
	///							[Limits:  0 <= value < #DT_CROWD_MAX_OBSTAVOIDANCE_PARAMS]
	/// @return The requested configuration.
	const dtObstacleAvoidanceParams* getObstacleAvoidanceParams(const int idx) const;
	
	/// Gets the specified agent from the pool.
	///	 @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	/// @return The requested agent.
	const dtCrowdAgent* getAgent(const int idx);

	/// Gets the specified agent from the pool.
	///	 @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	/// @return The requested agent.
	dtCrowdAgent* getEditableAgent(const int idx);

	/// The maximum number of agents that can be managed by the object.
	/// @return The maximum number of agents.
	int getAgentCount() const;
	
	/// Adds a new agent to the crowd.
	///  @param[in]		pos		The requested position of the agent. [(x, y, z)]
	///  @param[in]		params	The configutation of the agent.
	/// @return The index of the agent in the agent pool. Or -1 if the agent could not be added.
	int addAgent(const float* pos, const dtCrowdAgentParams* params);

	/// Updates the specified agent's configuration.
	///  @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	///  @param[in]		params	The new agent configuration.
	void updateAgentParameters(const int idx, const dtCrowdAgentParams* params);

	/// Removes the agent from the crowd.
	///  @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	void removeAgent(const int idx);
	
	/// Submits a new move request for the specified agent.
	///  @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	///  @param[in]		ref		The position's polygon reference.
	///  @param[in]		pos		The position within the polygon. [(x, y, z)]
	/// @return True if the request was successfully submitted.
	bool requestMoveTarget(const int idx, dtPolyRef ref, const float* pos);

	/// Submits a new move request for the specified agent.
	///  @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	///  @param[in]		vel		The movement velocity. [(x, y, z)]
	/// @return True if the request was successfully submitted.
	bool requestMoveVelocity(const int idx, const float* vel);

	/// Resets any request for the specified agent.
	///  @param[in]		idx		The agent index. [Limits: 0 <= value < #getAgentCount()]
	/// @return True if the request was successfully reseted.
	bool resetMoveTarget(const int idx);

	/// Gets the active agents int the agent pool.
	///  @param[out]	agents		An array of agent pointers. [(#dtCrowdAgent *) * maxAgents]
	///  @param[in]		maxAgents	The size of the crowd agent array.
	/// @return The number of agents returned in @p agents.
	int getActiveAgents(dtCrowdAgent** agents, const int maxAgents);

	/// Updates the steering and positions of all agents.
	///  @param[in]		dt		The time, in seconds, to update the simulation. [Limit: > 0]
	///  @param[out]	debug	A debug object to load with debug information. [Opt]
	void update(const float dt, dtCrowdAgentDebugInfo* debug);
	
	/// Gets the filter used by the crowd.
	/// @return The filter used by the crowd.
	inline const dtQueryFilter* getFilter(const int i) const { return (i >= 0 && i < DT_CROWD_MAX_QUERY_FILTER_TYPE) ? &m_filters[i] : 0; }
	
	/// Gets the filter used by the crowd.
	/// @return The filter used by the crowd.
	inline dtQueryFilter* getEditableFilter(const int i) { return (i >= 0 && i < DT_CROWD_MAX_QUERY_FILTER_TYPE) ? &m_filters[i] : 0; }

	/// Gets the search extents [(x, y, z)] used by the crowd for query operations. 
	/// @return The search extents used by the crowd. [(x, y, z)]
	const float* getQueryExtents() const { return m_ext; }
	
	/// Gets the velocity sample count.
	/// @return The velocity sample count.
	inline int getVelocitySampleCount() const { return m_velocitySampleCount; }
	
	/// Gets the crowd's proximity grid.
	/// @return The crowd's proximity grid.
	const dtProximityGrid* getGrid() const { return m_grid; }

	/// Gets the crowd's path request queue.
	/// @return The crowd's path request queue.
	const dtPathQueue* getPathQueue() const { return &m_pathq; }

	/// Gets the query object used by the crowd.
	const dtNavMeshQuery* getNavMeshQuery() const { return m_navquery; }
};

/// Allocates a crowd object using the Detour allocator.
/// @return A crowd object that is ready for initialization, or null on failure.
///  @ingroup crowd
dtCrowd* dtAllocCrowd();

/// Frees the specified crowd object using the Detour allocator.
///  @param[in]		ptr		A crowd object allocated using #dtAllocCrowd
///  @ingroup crowd
void dtFreeCrowd(dtCrowd* ptr);


#endif // DETOURCROWD_H



*/
/**
 * Members in this module implement local steering and dynamic avoidance features.
 * 
 * The crowd is the big beast of the navigation features. It not only handles a lot of the path management for you, but
 * also local steering and dynamic avoidance between members of the crowd. I.e. It can keep your agents from running
 * into each other.
 * 
 * Main class: Crowd
 * 
 * The #dtNavMeshQuery and #dtPathCorridor classes provide perfectly good, easy to use path planning features. But in
 * the end they only give you points that your navigation client should be moving toward. When it comes to deciding
 * things like agent velocity and steering to avoid other agents, that is up to you to implement. Unless, of course, you
 * decide to use Crowd.
 * 
 * Basically, you add an agent to the crowd, providing various configuration settings such as maximum speed and
 * acceleration. You also provide a local target to move toward. The crowd manager then provides, with every update, the
 * new agent position and velocity for the frame. The movement will be constrained to the navigation mesh, and steering
 * will be applied to ensure agents managed by the crowd do not collide with each other.
 * 
 * This is very powerful feature set. But it comes with limitations.
 * 
 * The biggest limitation is that you must give control of the agent's position completely over to the crowd manager.
 * You can update things like maximum speed and acceleration. But in order for the crowd manager to do its thing, it
 * can't allow you to constantly be giving it overrides to position and velocity. So you give up direct control of the
 * agent's movement. It belongs to the crowd.
 * 
 * The second biggest limitation revolves around the fact that the crowd manager deals with local planning. So the
 * agent's target should never be more than 256 polygons away from its current position. If it is, you risk your agent
 * failing to reach its target. So you may still need to do long distance planning and provide the crowd manager with
 * intermediate targets.
 * 
 * Other significant limitations:
 * 
 * - All agents using the crowd manager will use the same #dtQueryFilter. - Crowd management is relatively expensive.
 * The maximum agents under crowd management at any one time is between 20 and 30. A good place to start is a maximum of
 * 25 agents for 0.5ms per frame.
 * 
 * @note This is a summary list of members. Use the index or search feature to find minor members.
 * 
 * @struct dtCrowdAgentParams
 * @see CrowdAgent, Crowd::addAgent(), Crowd::updateAgentParameters()
 * 
 * @var dtCrowdAgentParams::obstacleAvoidanceType
 * @par
 * 
 * 		#dtCrowd permits agents to use different avoidance configurations. This value is the index of the
 *      #dtObstacleAvoidanceParams within the crowd.
 * 
 * @see dtObstacleAvoidanceParams, dtCrowd::setObstacleAvoidanceParams(), dtCrowd::getObstacleAvoidanceParams()
 * 
 * @var dtCrowdAgentParams::collisionQueryRange
 * @par
 * 
 * 		Collision elements include other agents and navigation mesh boundaries.
 * 
 *      This value is often based on the agent radius and/or maximum speed. E.g. radius * 8
 * 
 * @var dtCrowdAgentParams::pathOptimizationRange
 * @par
 * 
 * 		Only applicalbe if #updateFlags includes the #DT_CROWD_OPTIMIZE_VIS flag.
 * 
 *      This value is often based on the agent radius. E.g. radius * 30
 * 
 * @see dtPathCorridor::optimizePathVisibility()
 * 
 * @var dtCrowdAgentParams::separationWeight
 * @par
 * 
 * 		A higher value will result in agents trying to stay farther away from each other at the cost of more difficult
 *      steering in tight spaces.
 *
 */
public class Crowd {

	static final int MAX_ITERS_PER_UPDATE = 100;

	static final int MAX_PATHQUEUE_NODES = 4096;
	static final int MAX_COMMON_NODES = 512;

	/// The maximum number of neighbors that a crowd agent can take into account
	/// for steering decisions.
	/// @ingroup crowd
	static final int DT_CROWDAGENT_MAX_NEIGHBOURS = 6;

	/// The maximum number of corners a crowd agent will look ahead in the path.
	/// This value is used for sizing the crowd agent corner buffers.
	/// Due to the behavior of the crowd manager, the actual number of useful
	/// corners will be one less than this number.
	/// @ingroup crowd
	static final int DT_CROWDAGENT_MAX_CORNERS = 4;

	/// The maximum number of crowd avoidance configurations supported by the
	/// crowd manager.
	/// @ingroup crowd
	/// @see dtObstacleAvoidanceParams, dtCrowd::setObstacleAvoidanceParams(), dtCrowd::getObstacleAvoidanceParams(),
	///		 dtCrowdAgentParams::obstacleAvoidanceType
	static final int DT_CROWD_MAX_OBSTAVOIDANCE_PARAMS = 8;

	/// The maximum number of query filter types supported by the crowd manager.
	/// @ingroup crowd
	/// @see dtQueryFilter, dtCrowd::getFilter() dtCrowd::getEditableFilter(),
	///		dtCrowdAgentParams::queryFilterType
	static final int DT_CROWD_MAX_QUERY_FILTER_TYPE = 16;

	/// Provides neighbor data for agents managed by the crowd.
	/// @ingroup crowd
	/// @see dtCrowdAgent::neis, dtCrowd
	class CrowdNeighbour
	{
		int idx;		///< The index of the neighbor in the crowd.
		float dist;		///< The distance between the current agent and the neighbor.
	};


	/// Configuration parameters for a crowd agent.
	/// @ingroup crowd
	class CrowdAgentParams
	{
		float radius;						///< Agent radius. [Limit: >= 0]
		float height;						///< Agent height. [Limit: > 0]
		float maxAcceleration;				///< Maximum allowed acceleration. [Limit: >= 0]
		float maxSpeed;						///< Maximum allowed speed. [Limit: >= 0]

		/// Defines how close a collision element must be before it is considered for steering behaviors. [Limits: > 0]
		float collisionQueryRange;

		float pathOptimizationRange;		///< The path visibility optimization range. [Limit: > 0]

		/// How aggresive the agent manager should be at avoiding collisions with this agent. [Limit: >= 0]
		float separationWeight;

		/// Flags that impact steering behavior. (See: #UpdateFlags)
		int updateFlags;

		/// The index of the avoidance configuration to use for the agent. 
		/// [Limits: 0 <= value <= #DT_CROWD_MAX_OBSTAVOIDANCE_PARAMS]
		int obstacleAvoidanceType;	

		/// The index of the query filter used by this agent.
		int queryFilterType;

		/// User defined data attached to the agent.
		Object userData;
	};


	float tween(float t, float t0, float t1) {
		return clamp((t - t0) / (t1 - t0), 0.0f, 1.0f);
	}

	void addNeighbour(int idx, float dist, List<CrowdNeighbour> neis) {
		// Insert neighbour based on the distance.
		CrowdNeighbour nei = new CrowdNeighbour();
		nei.idx = idx;
		nei.dist = dist;
		neis.add(nei);
		Collections.sort(neis, (o1, o2) -> Float.compare(o1.dist, o2.dist));
	}

	List<CrowdNeighbour> getNeighbours(float[] pos, float height, float range, CrowdAgent skip, List<CrowdAgent> agents,
			ProximityGrid grid) {

		List<CrowdNeighbour> result = new ArrayList<>();
		Set<Integer> ids = grid.queryItems(pos[0] - range, pos[2] - range, pos[0] + range, pos[2] + range);

		for (int id : ids) {
			CrowdAgent ag = agents.get(id);

			if (ag == skip)
				continue;

			// Check for overlap.
			float[] diff = vSub(pos, ag.npos);
			if (Math.abs(diff[1]) >= (height + ag.params.height) / 2.0f)
				continue;
			diff[1] = 0;
			float distSqr = vLenSqr(diff);
			if (distSqr > sqr(range))
				continue;

			addNeighbour(id, distSqr, result);
		}
		return result;

	}

}