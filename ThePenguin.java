/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package game;
import java.util.Random;
/**
 *
 * @author smart
 */
public class ThePenguin implements IAgent
{
    int index;
    int nearenemy;
    int counter, dodges, attack, defend, kill;
    
    Random ran;
    
    ThePenguin()
    {
        reset();
        ran = new Random();
        index = 0;
        counter =0;
    }
    public void reset() 
    {
        index = 0;
        counter = 0;
    }
    public static float sq_dist(float x1, float y1, float x2, float y2) {
		return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
	}
    void forkevade(Model m, int i, float bombX, float bombY) {
		dodges++;

		float bestX = m.getX(i);
		float bestY = m.getY(i);
		float bestDodge = 0;
		for(int sim = 0; sim < 8; sim++) { // try 8 candidate destinations
			float x = (float)(m.getX(i)+(Math.cos((Math.PI*sim)/4)*Model.BLAST_RADIUS));
			float y = (float)(m.getY(i)+(Math.sin((Math.PI*sim)/4)*Model.BLAST_RADIUS));

			// Fork the universe and simulate it for 10 time-steps
			Controller cFork = m.getController().fork(new ThePenguinShadow(x, y), new EnemyShadow());
			Model mFork = cFork.getModel();
			for(int j = 0; j < 10; j++)
				cFork.update();

			// See how close the current sprite got to the opponent's flag in the forked universe
			float dodge = sq_dist(mFork.getX(i), mFork.getY(i), bombX, bombY);

			if(dodge > bestDodge) {
				bestDodge = dodge;
				bestX = x;
				bestY = y;
			}
		}

		// Head for the point that worked out best in simulation
		m.setDestination(i, bestX, bestY);
	}
    float nearestTarget(Model m, float x, float y) {
		index = -1;
		float max_float = Float.MAX_VALUE;
		for(int i = 0; i < m.getBombCount(); i++) {
			float d = sq_dist(x, y, m.getBombTargetX(i), m.getBombTargetY(i));
			if(d < max_float) {
				max_float = d;
				index = i;
			}
		}
		return max_float;
	}
    
    void evade(Model m, int i) {
		if(nearestTarget(m, m.getX(i), m.getY(i)) <= 2.0f * Model.BLAST_RADIUS * Model.BLAST_RADIUS) {
			if (i == defend && Model.XFLAG > m.getX(i) && m.getEnergySelf(i) >= .5){
				float dx;
				dx = m.getX(i) + m.getBombTargetX(index);
				float dy;
				if (m.getY(i) > Model.YFLAG)
					 dy = m.getY(i) + (m.getBombTargetY(index)+5);
				else
					dy = m.getY(i) - (m.getBombTargetY(index)+5);
				if(dx == 0 && dy == 0)
					dx = 1.0f;
				m.setDestination(i, m.getX(i) + dx, m.getY(i) + dy);
			}
			else
				forkevade(m, i, m.getBombTargetX(index), m.getBombTargetY(index));
			dodges++;
		}
	}
    
    float nearestOpponentToFlag(Model m) {
		index = -1;
		float max_float = Float.MAX_VALUE;
		for(int i = 0; i < m.getSpriteCountOpponent(); i++) {
			if (!(m.getEnergyOpponent(i) < 0)) {
				float d = sq_dist(Model.XFLAG, Model.YFLAG, m.getXOpponent(i), m.getYOpponent(i));
				if(d < max_float) {
					max_float = d;
					index = i;
				}
			}
		}
		return max_float;
	}
    void beAttacker(Model m, int i) {
		float X = m.getX(i);
		float Y = m.getY(i);

		// Find the opponent nearest to me
		nearestOpponentToFlag(m);


		if(index >= 0) {
			float eX = m.getXOpponent(index);
			float eY = m.getYOpponent(index);

			if(m.getEnergySelf(i) >= m.getEnergyOpponent(index)) {

				// Get close enough to throw a bomb at the enemy
				float dx = X - eX;
				float dy = Y - eY;
				float t = 1.0f / Math.max(Model.EPSILON, (float)Math.sqrt(dx * dx + dy * dy));
				dx *= t;
				dy *= t;
				if (counter%5==0)
					bestChoice(m, i, eX + dx * (Model.MAX_THROW_RADIUS - Model.EPSILON), eY + dy * (Model.MAX_THROW_RADIUS - Model.EPSILON));
				else
					m.setDestination(i, eX + dx * (Model.MAX_THROW_RADIUS - Model.EPSILON), eY + dy * (Model.MAX_THROW_RADIUS - Model.EPSILON));

				// Throw bombs
				if(sq_dist(eX, eY, m.getX(i), m.getY(i)) <= Model.MAX_THROW_RADIUS * Model.MAX_THROW_RADIUS)
					m.throwBomb(i, eX, eY);
			}
			else {

				// If the opponent is close enough to shoot at me...
				if(sq_dist(eX, eY, X, Y) <= (Model.MAX_THROW_RADIUS + Model.BLAST_RADIUS) * (Model.MAX_THROW_RADIUS + Model.BLAST_RADIUS) + 5.0f) {
						bestChoice(m, i, X + 10.0f * (X - eX), Y + 10.0f * (Y - eY));
				}
				else {
						m.setDestination(i, X, Y); // Rest
				}
			}
		}
		else {
			// Head for the opponent's flag
			m.setDestination(i, Model.XFLAG_OPPONENT - Model.MAX_THROW_RADIUS + 1, Model.YFLAG_OPPONENT);

			// Shoot at the flag if I can hit it
			if(sq_dist(m.getX(i), m.getY(i), Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT) <= Model.MAX_THROW_RADIUS * Model.MAX_THROW_RADIUS) {
				m.throwBomb(i, Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT);
			}
		}



		// Try not to die
		evade(m, i);
	}
    float bestAttacker(Model m) {
		attack = -1;
		float dd = Float.MAX_VALUE;
		for(int i = 0; i < m.getSpriteCountSelf(); i++) {
			if(m.getEnergySelf(i) < 0)
				continue; // don't care about dead agents
			float d = sq_dist(Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT, m.getX(i), m.getY(i));
			if(d < dd) {
				dd = d;
				attack = i;
			}
		}
		return dd;
	}
    
    float nearestOpponent(Model m, float x, float y) 
    {
		index = -1;
		float dd = Float.MAX_VALUE;
		for(int i = 0; i < m.getSpriteCountOpponent(); i++) {
			if(m.getEnergyOpponent(i) < 0)
				continue; // don't care about dead opponents
			float d = sq_dist(x, y, m.getXOpponent(i), m.getYOpponent(i));
			if(d < dd) {
				dd = d;
				index = i;
			}
		}
		return dd;
	}
    void beAggressor(Model m, int i) 
        {
		float myX = m.getX(i);
		float myY = m.getY(i);

		// Find the opponent nearest to me
		nearestOpponent(m, myX, myY);
		if(index >= 0) {
			float enemyX = m.getXOpponent(index);
			float enemyY = m.getYOpponent(index);

			if(m.getEnergySelf(i) >= m.getEnergyOpponent(index)) {

				// Get close enough to throw a bomb at the enemy
				float dx = myX - enemyX;
				float dy = myY - enemyY;
				float t = 1.0f / Math.max(Model.EPSILON, (float)Math.sqrt(dx * dx + dy * dy));
				dx *= t;
				dy *= t;
				m.setDestination(i, enemyX + dx * (Model.MAX_THROW_RADIUS - Model.EPSILON), enemyY + dy * (Model.MAX_THROW_RADIUS - Model.EPSILON));

				// Throw bombs
				if(sq_dist(enemyX, enemyY, m.getX(i), m.getY(i)) <= Model.MAX_THROW_RADIUS * Model.MAX_THROW_RADIUS)
					m.throwBomb(i, enemyX, enemyY);
			}
			else {

				// If the opponent is close enough to shoot at me...
				if(sq_dist(enemyX, enemyY, myX, myY) <= (Model.MAX_THROW_RADIUS + Model.BLAST_RADIUS) * (Model.MAX_THROW_RADIUS + Model.BLAST_RADIUS)) {
					m.setDestination(i, myX + 10.0f * (myX - enemyX), myY + 10.0f * (myY - enemyY)); // Flee
				}
				else {
					m.setDestination(i, myX, myY); // Rest
				}
			}
		}
		else {
			// Head for the opponent's flag
			m.setDestination(i, Model.XFLAG_OPPONENT - Model.MAX_THROW_RADIUS + 1, Model.YFLAG_OPPONENT);

			// Shoot at the flag if I can hit it
			if(sq_dist(m.getX(i), m.getY(i), Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT) <= Model.MAX_THROW_RADIUS * Model.MAX_THROW_RADIUS) {
				m.throwBomb(i, Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT);
			}
		}

		// Try not to die
		avoidBombs(m, i);
	}
    void avoidBombs(Model m, int i) {
		if(nearestBombTarget(m, m.getX(i), m.getY(i)) <= 2.0f * Model.BLAST_RADIUS * Model.BLAST_RADIUS) {
			float dx = m.getX(i) - m.getBombTargetX(index);
			float dy = m.getY(i) - m.getBombTargetY(index);
			if(dx == 0 && dy == 0)
				dx = 1.0f;
			m.setDestination(i, m.getX(i) + dx * 10.0f, m.getY(i) + dy * 10.0f);
		}
	}
    float nearestBombTarget(Model m, float x, float y) {
		index = -1;
		float dd = Float.MAX_VALUE;
		for(int i = 0; i < m.getBombCount(); i++) {
			float d = sq_dist(x, y, m.getBombTargetX(i), m.getBombTargetY(i));
			if(d < dd) {
				dd = d;
				index = i;
			}
		}
		return dd;
	}
    public void bestChoice(Model m, int i, float goalx, float goalY)
    {
        //beAttacker(m, i);
        /*for(int i = 0; i < 3; i++)
        {
            if(index%5==0)
            {
                for(int iter = 0; iter < 20; iter++)
                {
                    
                }
            }
        }*/
                float bestX = m.getX(i);
		float bestY = m.getY(i);
		float best_sqdist = 0;

		float startX = m.getX(i);
		float startY = m.getY(i);

		for(int sim = 0; sim < 8 ; sim++) { // try 8 candidate destinations
			float x = (float)(m.getX(i)+(Math.cos((Math.PI*sim)/4)*100));
			float y = (float)(m.getY(i)+(Math.sin((Math.PI*sim)/4)*100));

			// Fork the universe and simulate it for 10 time-steps
			Controller cFork = m.getController().fork(new ThePenguinShadow(x, y), new EnemyShadow());
			Model mFork = cFork.getModel();
			for(int j = 0; j < 10; j++)
				cFork.update();
			// See how much ground was covered in the forked universe
			float sqd = sq_dist(mFork.getX(i), mFork.getY(i), startX, startY);
			if(sqd > best_sqdist) 
                        {
				best_sqdist = sqd;
				bestX = x;
				bestY = y;
			}
		}
		// Head for the point that worked out best in simulation
		m.setDestination(i, bestX, bestY);
		// Shoot at the flag if I can hit it
                
		if(sq_dist(m.getX(i), m.getY(i), Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT) <= Model.MAX_THROW_RADIUS * Model.MAX_THROW_RADIUS)
			m.throwBomb(i, Model.XFLAG_OPPONENT, Model.YFLAG_OPPONENT);
        
        
    }
    public void update(Model m)
    {
        
        for(int i = 0; i < m.getSpriteCountSelf(); i++)
        {
            //bestChoice(m, i, m.getXOpponent(defend), m.getYOpponent(defend));
            beAttacker(m, i);
        }
        index++;
        counter++;
    }
    
     static class ThePenguinShadow implements IAgent
     {
         float dx;
         float dy;
        ThePenguinShadow(float x, float y)
        {
            dx = x;
            dy = y;
        }
        public void reset() 
        {
            
        }
        public void update(Model m) 
        {
            for(int i = 0; i < 3; i++) {
				if (dx > 0 && dx < Model.XMAX && dy > 0 && dy < Model.YMAX)
					m.setDestination(i, dx, dy);
			}
        }
    }
     static class EnemyShadow implements IAgent
     {
         int index;
        EnemyShadow()
        {
            
        }
        public void reset() 
        {
            
        }
        float nearestOpponent(Model m, float x, float y) {
			index = -1;
			float dd = Float.MAX_VALUE;
			for(int i = 0; i < m.getSpriteCountOpponent(); i++) {
				if(m.getEnergyOpponent(i) < 0)
					continue; // don't care about dead opponents
				float d = sq_dist(x, y, m.getXOpponent(i), m.getYOpponent(i));
				if(d < dd) {
					dd = d;
					index = i;
				}
			}
			return dd;
		}
        float nearestTarget(Model m, float x, float y) {
			index = -1;
			float dd = Float.MAX_VALUE;
			for(int i = 0; i < m.getBombCount(); i++) {
				float d = sq_dist(x, y, m.getBombTargetX(i), m.getBombTargetY(i));
				if(d < dd) {
					dd = d;
					index = i;
				}
			}
			return dd;
		}
        void evade(Model m, int i) {
			if(nearestTarget(m, m.getX(i), m.getY(i)) <= 2.0f * Model.BLAST_RADIUS * Model.BLAST_RADIUS) {
				float dx;
				dx = m.getX(i) + m.getBombTargetX(index);
				float dy;
				if (m.getY(i) > Model.YFLAG_OPPONENT)
					 dy = m.getY(i) - m.getBombTargetY(index);
				else
					dy = m.getY(i) + m.getBombTargetY(index);
				if(dx == 0 && dy == 0)
					dx = 1.0f;
				m.setDestination(i, m.getX(i) + dx, m.getY(i) + dy);
			}
		}
        public void update(Model m) 
        {
            for(int i = 0; i < m.getSpriteCountSelf(); i++) {
				nearestOpponent(m, m.getX(i), m.getY(i));

				if(index >= 0) {
					float eX = m.getXOpponent(index);
					float eY = m.getYOpponent(index);
					if(sq_dist(eX, eY, m.getX(i), m.getY(i)) <= Model.MAX_THROW_RADIUS * Model.MAX_THROW_RADIUS)
						m.throwBomb(i, eX, eY);
				}
				evade(m, i);
			}
        }
    }
    
    
    
}
