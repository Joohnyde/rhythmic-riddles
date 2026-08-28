import { Team } from './team.model';

export interface CategoryPreview {
  title: string;
  image: string;
}

/**
 * Backend selected-category snapshot (`LastCategory`). This is distinct from the albums collection:
 * it describes the category currently chosen/waiting to start and never controls frontend list order.
 */
export interface LastCategory {
  categoryId: string;
  chosenCategoryPreview: CategoryPreview;
  pickedByTeam: Team | null;
  started: boolean;
  /** Backend `LastCategory.ordinalNumber` is a primitive int and is always present/non-null on wire. */
  ordinalNumber: number;
}
