import { Team } from "./teams";
export class CategoryPreview{
    name !: string;
    image !: string;
}

export class LastCategory{
    categoryId !: string;
    chosenCategoryPreview !: CategoryPreview;
    pickedByTeam !: Team;
    started !: boolean;
    ordinalNumber !: number;
}