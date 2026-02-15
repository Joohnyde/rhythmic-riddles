import { Component, createEnvironmentInjector, EnvironmentInjector, inject, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom} from 'rxjs';
import { Router } from '@angular/router';
import { WebSocketService } from '../../services/web.socket.service';
import { Storage } from '../../utils/storage';
@Component({
  selector: 'comp-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  room_code : string = "";

  private envInjector = inject(EnvironmentInjector);
  private socket !: WebSocketService;

  @Input()
  public admin : boolean = false;

  private socket_position : number = 1
  private url_prefix : string = ""

  constructor(private storage: Storage, private router: Router){  }


  ngOnInit(){
    if(this.admin){
      this.socket_position = 0;
      this.url_prefix = "admin"
    }
  }

  private createServiceWithParam(code: string): WebSocketService {
    const childInjector = createEnvironmentInjector(
      [
        { provide: 'room_code', useValue: code },
        WebSocketService,
      ],
      this.envInjector, // parent
    );

    return childInjector.get(WebSocketService);
  }
  
  async login(){
    const code = this.room_code
    /* Backend expects a client-type prefix in the WS URL: 0=admin, 1=tv.
      We prepend it here so the handshake can infer which slot this client occupies.*/
    this.socket = this.createServiceWithParam(this.socket_position+code)
    this.storage.messageObservable = this.socket.getMessages();
    try{
      console.log(this.storage.messageObservable);
      const fwf = await firstValueFrom(this.storage.messageObservable);
      if(fwf.type == "welcome"){
        this.storage.code = code;
        this.router.navigate([this.url_prefix,fwf.stage]);
        // TODO: Implement JWT token auth
      }
    }catch(err){
      // Intentionally ignore: failed WS handshake / invalid code => stay on login screen.
    }
  }
}
