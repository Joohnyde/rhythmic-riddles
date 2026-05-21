import { Team } from './team.model';
export class CategoryPreview {
  title!: string;
  image!: string;
}
export class LastCategory {
  categoryId!: string;
  chosenCategoryPreview!: CategoryPreview;
  pickedByTeam!: Team;
  started!: boolean;
  ordinalNumber!: number;
}
