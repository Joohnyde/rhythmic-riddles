import { TeamScore } from "./team.scores";

export class Team{
    id !: string;
    name !: string;
    image !: string;

    public constructor(teamScore : TeamScore){
        this.id = teamScore.teamId
        this.name = teamScore.name
        this.image = teamScore.image
    }
}