import { NavLink } from "react-router-dom";
 

function NotFound() {
  return (
    
    <div className="not-found-container">
      <h1 className="title">404 Not Found</h1>
      <p className="">The page you are looking for does not exist.</p>
      <NavLink className="homeBtn" to="/">Go to Home</NavLink>
    </div>
       
  );
}

export default NotFound;