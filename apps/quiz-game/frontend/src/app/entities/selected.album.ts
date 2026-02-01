import { Team } from "./teams";
export class KategorijaPreview{
    naziv !: string;
    slika !: string;
}

export class SelectedAlbum{
    kategorija !: string;
    izabrana !: KategorijaPreview;
    birac !: Team;
    started !: boolean;
    rbr !: number;
}